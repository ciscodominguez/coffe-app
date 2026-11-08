#!/usr/bin/env bash
# Copia de seguridad: base de datos + imágenes de productos.
#
# Corre sola todas las noches (cron, 4:15). La guarda en deploy/backups/
# y la manda AFUERA del servidor con rclone (BACKUP_DESTINO en .env).
# Afuera se conservan BACKUP_DIAS días (14); en el servidor, 3 días.
#
# Uso:  scripts/backup.sh [etiqueta]      (por defecto "noche")
source "$(dirname "$0")/comun.sh"

etiqueta="${1:-noche}"
carpeta="$BACKUPS/$(date +%Y-%m-%d_%H%M)-$etiqueta"
al_salir() {
  local rc=$?
  if [ "$rc" -ne 0 ]; then
    rm -rf "$carpeta"
    avisar "Falló la copia de seguridad ($etiqueta)" "Revisá deploy/logs/backup.log en el servidor." high
  fi
}
trap al_salir EXIT

log "Copia de seguridad → $carpeta"
mkdir -p "$carpeta"

volcar_base dc "$carpeta/base.sql.gz"
volcar_imagenes cofe "$carpeta/imagenes.tar.gz"

# Datos para poder comprobar la copia después (probar-restauracion.sh).
{
  echo "fecha=$(date '+%Y-%m-%d %H:%M:%S')"
  echo "version_app=$(version_actual)"
  echo "version_base=$(version_base dc)"
  echo "cafes=$(printf 'SELECT COUNT(*) FROM businesses;\n' | sql dc)"
  echo "imagenes=$(tar -tzf "$carpeta/imagenes.tar.gz" | grep -vc '/$' || true)"
} > "$carpeta/info.txt"
(cd "$carpeta" && sha256sum base.sql.gz imagenes.tar.gz info.txt > SHA256SUMS)
log "Listo en el servidor: $(du -sh "$carpeta" | cut -f1)"

destino="$(env_get BACKUP_DESTINO)"
dias="$(env_get BACKUP_DIAS 14)"
if [ -z "$destino" ]; then
  avisar "Copia SOLO en el servidor" "Falta BACKUP_DESTINO en deploy/.env: si se rompe el servidor, se pierde todo. Ver docs/SERVIDOR.md, paso 4." high
elif ! command -v rclone >/dev/null; then
  avisar "Copia SOLO en el servidor" "Falta instalar rclone (sudo -v ; curl https://rclone.org/install.sh | sudo bash)." high
else
  rclone copy "$carpeta" "$destino/$(basename "$carpeta")" --retries 5 --low-level-retries 10
  # Afuera: se borra lo que tenga más de BACKUP_DIAS días.
  # (--b2-hard-delete y --drive-use-trash=false: que se borren de verdad y
  #  no ocupen lugar en B2 ni en la papelera de Google Drive.)
  rclone delete "$destino" --min-age "${dias}d" --retries 3 --b2-hard-delete --drive-use-trash=false || true
  rclone rmdirs "$destino" --leave-root || true
  log "Subida a $destino (se conservan $dias días)."
fi

# En el servidor: 3 días (las de "antes de actualizar", 7).
find "$BACKUPS" -mindepth 1 -maxdepth 1 -type d -name '*-noche' -mtime +2 -exec rm -rf {} +
find "$BACKUPS" -mindepth 1 -maxdepth 1 -type d ! -name '*-noche' -mtime +6 -exec rm -rf {} +

date +%s > "$ESTADO/ultimo-backup-ok"
log "Copia de seguridad OK."
