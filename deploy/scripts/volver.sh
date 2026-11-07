#!/usr/bin/env bash
# Volver a la versión anterior con un solo comando.
#
# Uso:
#   scripts/volver.sh                  vuelve a la versión anterior
#   scripts/volver.sh <version>        vuelve a una versión puntual (ver deploy/estado/historial)
#
# Si la versión nueva había cambiado la base (migración V20, V21…), hay que
# elegir:
#   --con-base   vuelve también la base a la copia de antes de actualizar
#                (lo cargado desde la actualización SE PIERDE; si se actualizó
#                 de noche con los cafés cerrados, no hay nada que perder)
#   --sin-base   deja la base como está (los datos quedan, pero la versión
#                vieja podría no andar con la base nueva)
source "$(dirname "$0")/comun.sh"

destino=""; modo=""; automatico=0
for a in "$@"; do
  case "$a" in
    --con-base) modo=con ;;
    --sin-base) modo=sin ;;
    --automatico) automatico=1 ;;
    -*) falla "opción desconocida: $a" ;;
    *) destino="$a" ;;
  esac
done

actual="$(version_actual)"
historial="$ESTADO/historial"
[ -f "$historial" ] || falla "no hay historial de versiones todavía"
if [ -z "$destino" ]; then
  destino="$(awk -v v="$actual" '$3 == v { for (i = 4; i <= NF; i++) if ($i ~ /^anterior=/) { sub(/^anterior=/, "", $i); a = $i } } END { print a }' "$historial")"
  [ -n "$destino" ] && [ "$destino" != "ninguna" ] || falla "no sé cuál es la versión anterior a $actual; indicala: scripts/volver.sh <version>"
fi
[ "$destino" != "$actual" ] || falla "ya estás en $destino"
docker image inspect "cofe-backend:$destino" "cofe-web:$destino" >/dev/null 2>&1 \
  || falla "la versión $destino ya no está guardada en el servidor (se guardan las 3 últimas)"

# La actualización que dejó atrás a "destino": cómo estaba la base y qué copia se hizo.
linea="$(grep " anterior=$destino " "$historial" | tail -n 1 || true)"
base_antes="$(sed -n 's/.* base_antes=V\([^ ]*\).*/\1/p' <<< "$linea")"
copia="$BACKUPS/$(sed -n 's/.* copia=\([^ ]*\).*/\1/p' <<< "$linea")"
base_ahora="$(version_base dc)"

cambio_base=0
if [ -n "$base_antes" ] && [ "$base_ahora" != "$base_antes" ]; then cambio_base=1; fi
if [ "$cambio_base" = 1 ] && [ -z "$modo" ]; then
  if [ "$automatico" = 1 ]; then
    modo=con
  else
    falla "Desde la versión $destino la base cambió (V$base_antes → V$base_ahora). Elegí:
  scripts/volver.sh $destino --con-base   (vuelve la base a la copia de antes de actualizar; se pierde lo cargado después)
  scripts/volver.sh $destino --sin-base   (deja los datos; la versión vieja podría no andar)"
  fi
fi
[ "$cambio_base" = 1 ] || modo=sin

if [ "$modo" = con ]; then
  [ -f "$copia/base.sql.gz" ] || falla "no encuentro la copia de antes de actualizar ($copia). Restaurá a mano con scripts/restaurar.sh"
  confirmar VOLVER "Se vuelve a $destino Y la base a la copia $(basename "$copia"). Lo cargado después se pierde."
else
  confirmar VOLVER "Se vuelve a la versión $destino (los datos quedan como están)."
fi

log "Volviendo: $actual → $destino (base: $modo cambios)"
if ! "$SCRIPTS/backup.sh" "antes-de-volver-a-$destino"; then
  [ "$modo" = con ] && falla "no se pudo guardar el estado actual antes de cambiar la base: no sigo"
  log "(no se pudo hacer la copia previa; sigo porque la base no se toca)"
fi
docker tag "cofe-backend:$destino" cofe-backend:actual
docker tag "cofe-web:$destino" cofe-web:actual
echo "$destino" > "$ESTADO/version-actual"
if [ "$modo" = con ]; then
  dc stop backend
  cargar_base dc "$copia/base.sql.gz"
fi
dc up -d
echo "$(date '+%Y-%m-%d %H:%M') $destino anterior=$actual base_antes=V$base_ahora copia=volver-base-$modo" >> "$historial"

if esperar_salud prod 300; then
  log "Listo: de vuelta en $destino (base en V$(version_base dc))."
  avisar "Se volvió a la versión $destino" "Desde $actual. Base: $([ "$modo" = con ] && echo "restaurada a la copia de antes de actualizar" || echo "sin cambios")." high
else
  avisar "Volver a $destino: el backend no arranca" "Hace falta revisar a mano: docker compose logs backend" urgent
  falla "la versión $destino tampoco arrancó (docker compose logs backend)"
fi
