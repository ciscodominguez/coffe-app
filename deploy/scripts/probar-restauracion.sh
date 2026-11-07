#!/usr/bin/env bash
# Prueba que una copia de seguridad SE PUEDE RESTAURAR (una vez por mes,
# sola, el día 1 a las 5:30). Si nunca la probaste, no sabés si funciona.
#
# Baja la copia más nueva DE AFUERA (así también prueba que el envío anda),
# la carga en una base aparte y temporal (nunca toca la de los cafés),
# revisa que esté todo y te avisa el resultado al celular.
#
# Uso:  scripts/probar-restauracion.sh [carpeta-de-backup]
source "$(dirname "$0")/comun.sh"

NOMBRE=cofe-prueba-restauracion
tmp="$(mktemp -d)"
avisado=0
al_salir() {
  local rc=$?
  docker rm -f "$NOMBRE" >/dev/null 2>&1 || true
  rm -rf "$tmp"
  if [ "$rc" -ne 0 ] && [ "$avisado" = 0 ]; then
    avisar "La prueba de restauración FALLÓ" "La copia de seguridad no se pudo restaurar. Revisá deploy/logs/restauracion.log en el servidor." urgent
  fi
}
trap al_salir EXIT

destino="$(env_get BACKUP_DESTINO)"
if [ -n "${1:-}" ]; then
  origen="$(carpeta_backup "$1")"
  cp -r "$origen"/. "$tmp/"
  de="la carpeta $origen"
elif [ -n "$destino" ] && command -v rclone >/dev/null; then
  ultima="$(rclone lsf "$destino" --dirs-only | sort | tail -n 1)"
  [ -n "$ultima" ] || falla "no hay copias en $destino"
  rclone copy "$destino/$ultima" "$tmp"
  de="$destino/$ultima (afuera del servidor)"
else
  origen="$(ultimo_backup)"
  [ -n "$origen" ] || falla "no hay copias"
  cp -r "$origen"/. "$tmp/"
  de="$origen (en el servidor: falta BACKUP_DESTINO)"
fi
log "Probando la copia de $de"

(cd "$tmp" && sha256sum -c --quiet SHA256SUMS) || falla "los archivos de la copia están dañados"
gzip -t "$tmp/base.sql.gz"
tar -tzf "$tmp/imagenes.tar.gz" >/dev/null
info() { grep -E "^$1=" "$tmp/info.txt" | cut -d= -f2-; }
fecha="$(info fecha)"; version_base="$(info version_base)"; cafes="$(info cafes)"; imagenes="$(info imagenes)"

# Base temporal, chica y aparte.
docker rm -f "$NOMBRE" >/dev/null 2>&1 || true
docker run -d --name "$NOMBRE" --memory 450m -e MYSQL_ROOT_PASSWORD=prueba -e MYSQL_DATABASE=cofe_app \
  mysql:8.0 --innodb-buffer-pool-size=64M --performance-schema=OFF --skip-log-bin >/dev/null
t=0
until docker exec "$NOMBRE" sh -c 'MYSQL_PWD=prueba mysqladmin -uroot -h 127.0.0.1 ping --silent' >/dev/null 2>&1; do
  sleep 3; t=$((t + 3)); [ "$t" -lt 240 ] || falla "la base temporal no arrancó"
done

inicio=$(date +%s)
zcat "$tmp/base.sql.gz" | docker exec -i "$NOMBRE" sh -c 'MYSQL_PWD=prueba exec mysql -uroot cofe_app'
segundos=$(( $(date +%s) - inicio ))

q() { printf '%s\n' "$1" | docker exec -i "$NOMBRE" sh -c 'MYSQL_PWD=prueba exec mysql -uroot -N -B cofe_app'; }
tablas="$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'cofe_app';")"
migracion="$(q 'SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;')"
fallidas="$(q 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0;')"
cafes_r="$(q 'SELECT COUNT(*) FROM businesses;')"
pedidos_r="$(q 'SELECT COUNT(*) FROM orders;')"
usuarios_r="$(q 'SELECT COUNT(*) FROM users;')"
imagenes_r="$(tar -tzf "$tmp/imagenes.tar.gz" | grep -vc '/$' || true)"

problemas=""
[ "$tablas" -ge 30 ] || problemas="$problemas solo $tablas tablas;"
[ "$migracion" = "$version_base" ] || problemas="$problemas migración V$migracion (esperaba V$version_base);"
[ "$fallidas" = "0" ] || problemas="$problemas $fallidas migraciones fallidas;"
[ "$cafes_r" = "$cafes" ] || problemas="$problemas $cafes_r cafés (esperaba $cafes);"
[ "$imagenes_r" = "$imagenes" ] || problemas="$problemas $imagenes_r imágenes (esperaba $imagenes);"

resumen="Copia del $fecha: $cafes_r cafés, $usuarios_r usuarios, $pedidos_r pedidos, $imagenes_r imágenes, base en V$migracion. Restaurar tardó $segundos s."
if [ -n "$problemas" ]; then
  avisar "La prueba de restauración encontró problemas" "$resumen Problemas:$problemas" urgent
  avisado=1
  exit 1
fi
date +%s > "$ESTADO/ultima-prueba-restauracion-ok"
avisar "Copia de seguridad probada: OK" "$resumen" low
