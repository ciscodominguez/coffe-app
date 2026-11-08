#!/usr/bin/env bash
# Vuelve el sistema de verdad al estado de una copia de seguridad.
# Para emergencias (se rompió algo, se borraron datos). Todo lo que se
# cargó DESPUÉS de esa copia se pierde, por eso antes guarda una copia
# del estado actual ("antes-de-restaurar"), por las dudas.
#
# Uso:
#   scripts/restaurar.sh ultima               la copia más nueva del servidor
#   scripts/restaurar.sh <carpeta>            una de deploy/backups/
#   scripts/restaurar.sh --de-afuera <nombre> una copia de BACKUP_DESTINO
#                                             (para ver cuáles hay: rclone lsf <destino>)
#   Agregá --si al final para no tener que confirmar (lo usa volver.sh).
source "$(dirname "$0")/comun.sh"

for a in "$@"; do [ "$a" = "--si" ] && SI=1; done
tmp=""
trap '[ -n "$tmp" ] && rm -rf "$tmp"' EXIT

case "${1:-}" in
  ultima) carpeta="$(ultimo_backup)" ;;
  --de-afuera)
    [ -n "${2:-}" ] || falla "falta el nombre de la copia"
    tmp="$(mktemp -d)"
    rclone copy "$(env_get BACKUP_DESTINO)/$2" "$tmp"
    carpeta="$tmp" ;;
  ""|--si) falla "Uso: scripts/restaurar.sh ultima | <carpeta> | --de-afuera <nombre>" ;;
  *) carpeta="$(carpeta_backup "$1")" ;;
esac
[ -f "$carpeta/base.sql.gz" ] || falla "no encuentro una copia en: $carpeta"
(cd "$carpeta" && sha256sum -c --quiet SHA256SUMS) || falla "la copia está dañada"

info="$(grep -E '^(fecha|version_base|cafes)=' "$carpeta/info.txt" | tr '\n' ' ')"
confirmar RESTAURAR "Se va a volver a la copia: $info
Todo lo que se cargó después se pierde (antes se guarda una copia del estado actual)."

log "Guardando el estado actual por las dudas…"
"$SCRIPTS/backup.sh" antes-de-restaurar

log "Deteniendo el backend…"
dc stop backend
log "Cargando la base…"
cargar_base dc "$carpeta/base.sql.gz"
log "Cargando las imágenes…"
cargar_imagenes cofe "$carpeta/imagenes.tar.gz"
log "Arrancando…"
dc up -d backend
if esperar_salud prod 300; then
  log "Restauración terminada. Base en V$(version_base dc)."
  avisar "Sistema restaurado" "Se volvió a la copia: $info" high
else
  avisar "Restauración: el backend no arranca" "Revisá con: docker compose logs backend" urgent
  falla "el backend no respondió después de restaurar (mirá: docker compose logs backend)"
fi
