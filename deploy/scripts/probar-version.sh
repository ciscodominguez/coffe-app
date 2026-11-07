#!/usr/bin/env bash
# PASO 1 DE TODA ACTUALIZACIÓN: armar la versión nueva y probarla en una
# COPIA (staging), con una copia de los datos reales, sin tocar a los cafés.
#
# Uso:
#   scripts/probar-version.sh            arma la versión con el código de esta
#                                        carpeta y levanta la copia de prueba
#   scripts/probar-version.sh --listo    la probé y anda: apagar la copia y
#                                        dejarla lista para actualizar.sh
#   scripts/probar-version.sh --descartar  no anda: apagar y borrar la versión
#
# La copia se ve en http://localhost:8081. Desde tu compu:
#   ssh -L 8081:localhost:8081 usuario@IP-del-servidor   y abrís esa dirección.
#
# Armar la versión usa bastante memoria y procesador: hacelo fuera del
# horario de los cafés.
source "$(dirname "$0")/comun.sh"

case "${1:-}" in
  --listo)
    cand="$(cat "$ESTADO/candidata" 2>/dev/null || true)"
    [ -n "$cand" ] || falla "no hay ninguna versión en prueba"
    dcp down -v --remove-orphans
    echo "$cand" > "$ESTADO/candidata-probada"
    log "Versión $cand marcada como probada. Para ponerla: scripts/actualizar.sh"
    exit 0 ;;
  --descartar)
    cand="$(cat "$ESTADO/candidata" 2>/dev/null || true)"
    dcp down -v --remove-orphans || true
    if [ -n "$cand" ] && [ "$cand" != "$(version_actual)" ]; then
      docker image rm "cofe-backend:$cand" "cofe-web:$cand" >/dev/null 2>&1 || true
    fi
    rm -f "$ESTADO/candidata" "$ESTADO/candidata-probada"
    log "Versión de prueba descartada."
    exit 0 ;;
  "") ;;
  *) falla "Uso: scripts/probar-version.sh [--listo | --descartar]" ;;
esac

if ! en_ventana; then
  log "Aviso: estás fuera del horario de actualización ($(texto_ventana))."
  log "Armar la versión puede poner lenta la app un rato."
  confirmar SEGUIR "¿Seguir igual?"
fi

ver="$(date +%Y%m%d-%H%M)"
if git -C "$RAIZ" rev-parse --short HEAD >/dev/null 2>&1; then
  ver="$ver-$(git -C "$RAIZ" rev-parse --short HEAD)"
  log "Código: $(git -C "$RAIZ" log -1 --format='%h %s (%cr)')"
fi
log "Armando la versión $ver (tarda unos minutos)…"
docker build -t "cofe-backend:$ver" "$RAIZ/backend"
docker build -t "cofe-web:$ver" "$RAIZ/frontend"
echo "$ver" > "$ESTADO/candidata"
rm -f "$ESTADO/candidata-probada"

log "Copiando los datos reales de ahora a la copia de prueba…"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
volcar_base dc "$tmp/base.sql.gz"
volcar_imagenes cofe "$tmp/imagenes.tar.gz"

# Copia de prueba chica, para que entre junto al sistema de verdad.
export VERSION="$ver" SITIO=":80" URL_PUBLICA="http://localhost:8081" \
  JAVA_OPTS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError" \
  MYSQL_BUFFER="96M" MYSQL_MEM="450m" BACKEND_MEM="650m"
dcp down -v --remove-orphans >/dev/null 2>&1 || true
dcp up -d db
esperar_base dcp || falla "la base de la copia no arrancó"
cargar_base dcp "$tmp/base.sql.gz"
dcp create backend web
cargar_imagenes cofe-prueba "$tmp/imagenes.tar.gz"
dcp up -d

log "Esperando que arranque (acá corren las migraciones nuevas sobre los datos copiados)…"
if ! esperar_salud prueba 300; then
  dcp logs --tail 80 backend || true
  falla "la versión $ver NO arrancó en la copia de prueba (arriba, el final del log). No la subas: scripts/probar-version.sh --descartar"
fi

cat <<EOF

  ✔ Copia de prueba andando con la versión $ver (base en V$(version_base dcp)).

  1. Desde tu compu:  ssh -L 8081:localhost:8081 ${SUDO_USER:-$(whoami)}@IP-del-servidor
  2. Abrí http://localhost:8081 y entrá con tu usuario de siempre.
  3. Hacé la lista de 15 minutos (docs/PRUEBAS-OCTUBRE-2026.md, punto 9).
     En la copia NO salen mails ni cobros de Mercado Pago.
  4. Si anda:     scripts/probar-version.sh --listo
     Si no anda:  scripts/probar-version.sh --descartar

EOF
