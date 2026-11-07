#!/usr/bin/env bash
# Revisión automática cada 5 minutos (cron). Te avisa al celular (ntfy) si:
#   - el disco pasa el 80 % (AVISO_DISCO_PORCENTAJE),
#   - la app no responde desde internet (HTTPS incluido) dos veces seguidas,
#   - el backend o MySQL se reiniciaron solos (por ejemplo, sin memoria),
#   - la última copia de seguridad buena tiene más de 26 horas.
# Avisa una vez cuando empieza el problema y otra cuando se arregla.
#
# Si se cae el servidor entero este script tampoco corre: para eso está el
# monitor externo (UptimeRobot, ver docs/SERVIDOR.md, paso 7).
source "$(dirname "$0")/comun.sh"

# Avisa solo cuando cambia el estado. $1 = nombre, $2 = 1 si hay problema.
estado() {
  local nombre="$1" mal="$2" titulo="$3" msg="$4" ok_msg="$5" archivo="$ESTADO/alerta-$1"
  if [ "$mal" = 1 ] && [ ! -f "$archivo" ]; then
    date +%s > "$archivo"
    avisar "$titulo" "$msg" high
  elif [ "$mal" = 0 ] && [ -f "$archivo" ]; then
    rm -f "$archivo"
    avisar "Se normalizó: $nombre" "$ok_msg" low
  fi
}

# Disco
limite="$(env_get AVISO_DISCO_PORCENTAJE 80)"
uso="$(df -P / | awk 'NR == 2 { gsub("%", "", $5); print $5 }')"
estado disco "$([ "$uso" -ge "$limite" ] && echo 1 || echo 0)" \
  "Disco al $uso %" "Liberá espacio: sudo docker system prune  y revisá deploy/backups/. Ver docs/SERVIDOR.md." \
  "Disco al $uso %."

# La app desde internet (pasa por HTTPS, Caddy, backend y base)
dominio="$(env_get DOMINIO)"
if curl -fsS -m 15 "https://$dominio/api/v1/actuator/health" 2>/dev/null | grep -q '"UP"'; then
  rm -f "$ESTADO/salud-fallos"
  estado app 0 "" "" "La app volvió a responder."
else
  fallos=$(( $(cat "$ESTADO/salud-fallos" 2>/dev/null || echo 0) + 1 ))
  echo "$fallos" > "$ESTADO/salud-fallos"
  if [ "$fallos" -ge 2 ]; then
    estado app 1 "La app NO responde" "https://$dominio no contesta hace 10 minutos. Probá: cd deploy && docker compose ps" ""
  fi
fi

# Reinicios solos (Docker los levanta, pero conviene saberlo)
for servicio in backend db; do
  id="$(dc ps -q "$servicio" 2>/dev/null || true)"
  [ -n "$id" ] || continue
  n="$(docker inspect -f '{{.RestartCount}}' "$id" 2>/dev/null || echo 0)"
  previo="$(cat "$ESTADO/reinicios-$servicio" 2>/dev/null || echo "$n")"
  echo "$n" > "$ESTADO/reinicios-$servicio"
  if [ "$n" -gt "$previo" ]; then
    oom="$(docker inspect -f '{{.State.OOMKilled}}' "$id" 2>/dev/null || echo false)"
    avisar "Se reinició: $servicio" "Docker lo volvió a levantar solo.$([ "$oom" = true ] && echo " Fue por falta de memoria.") Si se repite, avisá." high
  fi
done

# Copia de seguridad al día
ultimo="$(cat "$ESTADO/ultimo-backup-ok" 2>/dev/null || echo 0)"
horas=$(( ($(date +%s) - ultimo) / 3600 ))
if [ -f "$ESTADO/version-actual" ]; then
  estado backup "$([ "$horas" -gt 26 ] && echo 1 || echo 0)" \
    "No hay copia de seguridad nueva" "La última buena es de hace $horas horas. Revisá deploy/logs/backup.log." \
    "Las copias de seguridad volvieron a andar."
fi
