#!/usr/bin/env bash
# PASO 2: poner en el sistema de verdad la versión ya probada en la copia.
#
#   - Solo en la ventana permitida: después del cierre, de lunes a jueves
#     (nunca viernes ni en horario de café). Se cambia en .env.
#   - Solo una versión que pasó por probar-version.sh (staging).
#   - Antes hace una copia de seguridad (por si la versión trae migraciones
#     de base: V20, V21…).
#   - La versión anterior queda guardada: si la nueva no arranca, VUELVE
#     SOLA a la anterior. Para volver a mano: scripts/volver.sh
#
# Uso:  scripts/actualizar.sh [--forzar-horario] [--sin-prueba]
#       (las dos opciones piden confirmación: son para emergencias)
source "$(dirname "$0")/comun.sh"

forzar=0; sin_prueba=0
for a in "$@"; do
  case "$a" in
    --forzar-horario) forzar=1 ;;
    --sin-prueba) sin_prueba=1 ;;
    *) falla "opción desconocida: $a" ;;
  esac
done

# 1) Horario
if ! en_ventana; then
  if [ "$forzar" = 1 ]; then
    confirmar FUERA-DE-HORARIO "Estás fuera del horario seguro ($(texto_ventana)). Los cafés pueden estar trabajando."
  else
    falla "Fuera del horario de actualización ($(texto_ventana)). Nunca un viernes ni con los cafés abiertos."
  fi
fi

# 2) Versión probada
cand="$(cat "$ESTADO/candidata" 2>/dev/null || true)"
probada="$(cat "$ESTADO/candidata-probada" 2>/dev/null || true)"
[ -n "$cand" ] || falla "No hay versión nueva. Primero: scripts/probar-version.sh"
if [ "$cand" != "$probada" ]; then
  [ "$sin_prueba" = 1 ] || falla "La versión $cand no se marcó como probada. Probala en la copia y corré: scripts/probar-version.sh --listo"
  confirmar SIN-PROBAR "La versión $cand NO se probó en la copia."
fi
docker image inspect "cofe-backend:$cand" "cofe-web:$cand" >/dev/null 2>&1 || falla "no encuentro las imágenes de $cand: volvé a correr probar-version.sh"

anterior="$(version_actual)"
[ "$cand" != "$anterior" ] || falla "La versión $cand ya está puesta."
log "Actualizando: $anterior → $cand"

# 3) Copia de seguridad antes de tocar nada
base_antes="$(version_base dc)"
"$SCRIPTS/backup.sh" "antes-de-$cand"
copia="$(ultimo_backup)"
echo "$(date '+%Y-%m-%d %H:%M') $cand anterior=$anterior base_antes=V$base_antes copia=$(basename "$copia")" >> "$ESTADO/historial"

# 4) Cambiar a la versión nueva
if [ "$anterior" != "ninguna" ]; then
  docker tag cofe-backend:actual "cofe-backend:$anterior"
  docker tag cofe-web:actual "cofe-web:$anterior"
fi
docker tag "cofe-backend:$cand" cofe-backend:actual
docker tag "cofe-web:$cand" cofe-web:actual
echo "$cand" > "$ESTADO/version-actual"
dc up -d

log "Esperando que la versión nueva responda…"
if esperar_salud prod 300; then
  rm -f "$ESTADO/candidata" "$ESTADO/candidata-probada"
  log "Listo: versión $cand andando (base en V$(version_base dc))."
  avisar "Actualizado a $cand" "Anda bien. Si notás algo raro: scripts/volver.sh" default
  # Se guardan las 3 últimas versiones para poder volver; el resto se borra.
  for repo in cofe-backend cofe-web; do
    docker image ls "$repo" --format '{{.Tag}}' | grep -v '^actual$' | sort -r | tail -n +4 \
      | while read -r tag; do docker image rm "$repo:$tag" >/dev/null 2>&1 || true; done
  done
  docker image prune -f >/dev/null 2>&1 || true
  docker builder prune -f --filter until=168h >/dev/null 2>&1 || true
  exit 0
fi

# 5) No arrancó: volver sola a la anterior
dc logs --tail 100 backend > "$LOGS/fallo-$cand.log" 2>&1 || true
avisar "La versión $cand NO arrancó" "Volviendo sola a $anterior. Log: deploy/logs/fallo-$cand.log" urgent
if [ "$anterior" = "ninguna" ]; then
  falla "no hay versión anterior a la que volver (log: deploy/logs/fallo-$cand.log)"
fi
SI=1 "$SCRIPTS/volver.sh" "$anterior" --automatico
falla "la versión $cand no arrancó; se volvió a $anterior (log: deploy/logs/fallo-$cand.log)"
