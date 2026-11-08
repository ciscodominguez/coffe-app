#!/usr/bin/env bash
# shellcheck disable=SC2016  # los $ entre comillas simples se expanden adentro del contenedor, a propósito
# Funciones que usan todos los scripts del servidor. No se corre solo.
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC2034  # la usan los scripts que cargan este archivo
RAIZ="$(cd "$DEPLOY_DIR/.." && pwd)"
ESTADO="$DEPLOY_DIR/estado"
BACKUPS="$DEPLOY_DIR/backups"
LOGS="$DEPLOY_DIR/logs"
# shellcheck disable=SC2034  # la usan los scripts que cargan este archivo
SCRIPTS="$DEPLOY_DIR/scripts"
ORIGEN="$PWD"   # desde dónde se llamó (para rutas relativas que pase el usuario)
mkdir -p "$ESTADO" "$BACKUPS" "$LOGS"
cd "$DEPLOY_DIR"
export TZ="${TZ:-America/Argentina/Buenos_Aires}"

# Lee una variable de deploy/.env sin ejecutarlo (tiene claves con puntos).
env_get() {
  local v=""
  if [ -f "$DEPLOY_DIR/.env" ]; then
    v="$(grep -E "^$1=" "$DEPLOY_DIR/.env" | tail -n 1 | cut -d= -f2- || true)"
  fi
  printf '%s' "${v:-${2:-}}"
}

log() { printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
falla() { log "ERROR: $*"; exit 1; }

# docker compose del sistema de verdad (proyecto "cofe") y de la copia de prueba.
dc() { docker compose "$@"; }
dcp() { docker compose -p cofe-prueba -f docker-compose.yml -f docker-compose.prueba.yml "$@"; }

# Aviso al celular con la app ntfy (AVISOS_NTFY_TEMA en .env).
# Uso: avisar "Título" "Mensaje" [prioridad: min|low|default|high|urgent]
avisar() {
  local tema
  tema="$(env_get AVISOS_NTFY_TEMA)"
  log "AVISO: $1 — $2"
  [ -n "$tema" ] || return 0
  curl -fsS -m 20 -H "Title: COFFE_APP" -H "Priority: ${3:-default}" -H "Tags: coffee" \
    --data-binary "$1"$'\n'"$2" "https://ntfy.sh/$tema" >/dev/null 2>&1 || log "(no se pudo mandar el aviso a ntfy)"
}

# Pide escribir una palabra para confirmar algo delicado. Con --si se saltea.
confirmar() {
  local palabra="$1" msg="$2" r
  [ "${SI:-0}" = "1" ] && return 0
  printf '%s\nEscribí %s para seguir: ' "$msg" "$palabra"
  read -r r
  [ "$r" = "$palabra" ] || falla "Cancelado."
}

# ¿El backend responde UP? $1 = "prod" (por defecto) o "prueba".
salud_ok() {
  if [ "${1:-prod}" = "prueba" ]; then
    curl -fsS -m 5 http://127.0.0.1:8081/api/v1/actuator/health 2>/dev/null | grep -q '"UP"'
  else
    dc exec -T backend wget -qO- http://127.0.0.1:3000/api/v1/actuator/health 2>/dev/null | grep -q '"UP"'
  fi
}

# Espera hasta $2 segundos a que el backend dé UP.
esperar_salud() {
  local cual="${1:-prod}" max="${2:-240}" t=0
  while [ "$t" -lt "$max" ]; do
    salud_ok "$cual" && return 0
    sleep 5
    t=$((t + 5))
  done
  return 1
}

# Espera a que MySQL acepte conexiones. $1 = función compose (dc o dcp).
esperar_base() {
  local f="${1:-dc}" t=0
  while [ "$t" -lt 180 ]; do
    if "$f" exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -uroot -h 127.0.0.1 ping --silent' >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
    t=$((t + 3))
  done
  return 1
}

# Corre SQL como root en la base. $1 = dc o dcp; el SQL entra por stdin.
sql() {
  "$1" exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B cofe_app'
}

# Vuelca la base completa a un .sql.gz. $1 = dc o dcp, $2 = archivo destino.
volcar_base() {
  local f="$1" destino="$2"
  "$f" exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF cofe_app' \
    | gzip -c > "$destino.tmp"
  zcat "$destino.tmp" | tail -n 1 | grep -q "Dump completed" || falla "el volcado de la base quedó incompleto"
  mv "$destino.tmp" "$destino"
}

# Carga un .sql.gz en la base (la vacía antes). $1 = dc o dcp, $2 = archivo.
cargar_base() {
  local f="$1" origen="$2"
  printf 'DROP DATABASE IF EXISTS cofe_app; CREATE DATABASE cofe_app CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON cofe_app.* TO `cofe_user`@`%%`;\n' \
    | "$f" exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
  zcat "$origen" | sql "$f"
}

# Volumen de imágenes de un proyecto ("cofe" o "cofe-prueba").
vol_imagenes() { printf '%s_imagenes' "${1:-cofe}"; }

# Guarda las imágenes de productos en un .tar.gz. $1 = proyecto, $2 = archivo.
volcar_imagenes() {
  docker run --rm --entrypoint tar -v "$(vol_imagenes "$1"):/datos:ro" cofe-backend:actual \
    -czf - -C /datos . > "$2.tmp"
  mv "$2.tmp" "$2"
}

# Reemplaza las imágenes de un proyecto por las de un .tar.gz.
cargar_imagenes() {
  docker run --rm -i --user 0 --entrypoint sh -v "$(vol_imagenes "$1"):/datos" cofe-backend:actual \
    -c 'rm -rf /datos/* && tar -xzf - -C /datos && chown -R 1000:1000 /datos' < "$2"
}

# Última migración aplicada (V19, V20…).
version_base() {
  printf 'SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1;\n' \
    | sql "${1:-dc}" 2>/dev/null || true
}

version_actual() { cat "$ESTADO/version-actual" 2>/dev/null || echo "ninguna"; }

# Carpeta de backup que indicó el usuario: ruta absoluta, relativa a donde
# estaba parado, o solo el nombre de una de deploy/backups/.
carpeta_backup() {
  case "$1" in
    /*) printf '%s' "$1" ;;
    *) if [ -d "$ORIGEN/$1" ]; then printf '%s' "$ORIGEN/$1"; else printf '%s' "$BACKUPS/$1"; fi ;;
  esac
}

# Carpeta de backup más nueva en el servidor.
ultimo_backup() { find "$BACKUPS" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1; }

# ¿Estamos en la ventana permitida para actualizar? (después del cierre,
# de lunes a jueves, nunca viernes). Se configura en .env.
en_ventana() {
  local dias desde hasta dmin dmax d h
  dias="$(env_get ACTUALIZAR_DIAS 1-4)"
  desde="$(env_get ACTUALIZAR_DESDE 23)"
  hasta="$(env_get ACTUALIZAR_HASTA 6)"
  dmin="${dias%-*}"
  dmax="${dias#*-}"
  d="$(date +%u)"
  h="$((10#$(date +%H)))"
  [ "$d" -ge "$dmin" ] && [ "$d" -le "$dmax" ] || return 1
  if [ "$desde" -gt "$hasta" ]; then
    [ "$h" -ge "$desde" ] || [ "$h" -lt "$hasta" ]
  else
    [ "$h" -ge "$desde" ] && [ "$h" -lt "$hasta" ]
  fi
}

texto_ventana() {
  printf 'días %s (1=lunes … 7=domingo), de %s a %s h' \
    "$(env_get ACTUALIZAR_DIAS 1-4)" "$(env_get ACTUALIZAR_DESDE 23)" "$(env_get ACTUALIZAR_HASTA 6)"
}
