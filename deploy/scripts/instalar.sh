#!/usr/bin/env bash
# Instalación en un servidor NUEVO (Ubuntu 22.04 o 24.04, 2 GB de RAM mínimo).
# Se corre una sola vez, con sudo, desde la carpeta del proyecto:
#
#   sudo bash deploy/scripts/instalar.sh
#
# Hace todo esto (si algo ya está hecho, lo saltea; se puede correr de nuevo):
#   1. Hora de Argentina, memoria de respaldo (swap) y firewall.
#   2. Instala Docker (arranca solo al prender el servidor) y rclone.
#   3. Límite de tamaño para los logs (Docker y del sistema).
#   4. Crea deploy/.env con claves al azar (después completás el dominio).
#   5. Programa las tareas automáticas: copia cada noche, revisión cada
#      5 minutos y prueba de restauración el día 1 de cada mes.
#   6. Arma la primera versión y la pone en marcha (HTTPS incluido).
source "$(dirname "$0")/comun.sh"

[ "$(id -u)" = 0 ] || falla "Correlo con sudo: sudo bash deploy/scripts/instalar.sh"
chmod +x "$SCRIPTS"/*.sh

# --- 1. Sistema --------------------------------------------------------------
ram_mb="$(free -m | awk '/^Mem:/ { print $2 }')"
if [ "$ram_mb" -lt 1800 ]; then
  log "OJO: el servidor tiene $ram_mb MB de RAM. Spring Boot y MySQL juntos necesitan 2 GB como mínimo."
  confirmar SEGUIR "¿Seguir igual?"
fi
timedatectl set-timezone America/Argentina/Buenos_Aires || true

if ! swapon --show | grep -q .; then
  log "Creando 2 GB de memoria de respaldo (swap)…"
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo 'vm.swappiness=10' > /etc/sysctl.d/90-cofe.conf && sysctl -p /etc/sysctl.d/90-cofe.conf >/dev/null
fi

if command -v ufw >/dev/null; then
  # SSH en el puerto que use este servidor (para no quedar afuera), web y HTTPS.
  for puerto in $(sshd -T 2>/dev/null | awk '$1 == "port" { print $2 }'); do ufw allow "$puerto/tcp" >/dev/null; done
  ufw allow OpenSSH >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; ufw allow 443/udp >/dev/null
  ufw --force enable >/dev/null
fi

# --- 2. Docker y rclone --------------------------------------------------------
if ! command -v docker >/dev/null; then
  log "Instalando Docker…"
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker >/dev/null
command -v rclone >/dev/null || {
  log "Instalando rclone…"
  apt-get install -y -qq unzip >/dev/null 2>&1 || true
  curl -fsSL https://rclone.org/install.sh | bash
}

# --- 3. Logs que no llenen el disco -----------------------------------------------
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "5" } }' > /etc/docker/daemon.json
  systemctl restart docker
fi
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=200M\n' > /etc/systemd/journald.conf.d/cofe.conf
systemctl restart systemd-journald || true
cat > /etc/logrotate.d/cofe-app <<EOF
$LOGS/*.log {
  weekly
  rotate 8
  compress
  missingok
  notifempty
  copytruncate
}
EOF

# --- 4. Configuración ---------------------------------------------------------------
if [ ! -f "$DEPLOY_DIR/.env" ]; then
  cp "$DEPLOY_DIR/.env.example" "$DEPLOY_DIR/.env"
  for k in MYSQL_ROOT_PASSWORD DB_PASSWORD JWT_SECRET MP_TOKEN_ENCRYPTION_KEY; do
    sed -i "s|^$k=\$|$k=$(openssl rand -hex 32)|" "$DEPLOY_DIR/.env"
  done
  chmod 600 "$DEPLOY_DIR/.env"
  log "Creé deploy/.env con claves al azar."
fi
dominio="$(env_get DOMINIO)"
if [ -z "$dominio" ] || [ "$dominio" = "app.tudominio.com.ar" ]; then
  falla "Completá DOMINIO (y lo demás que quieras) en deploy/.env:  nano $DEPLOY_DIR/.env   y volvé a correr este script."
fi

# --- 5. Tareas automáticas ----------------------------------------------------------
cat > /etc/cron.d/cofe-app <<EOF
# COFFE_APP — tareas automáticas (hora de Argentina). Ver docs/SERVIDOR.md.
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
15 4 * * * root $SCRIPTS/backup.sh noche >> $LOGS/backup.log 2>&1
*/5 * * * * root $SCRIPTS/vigilar.sh >> $LOGS/vigilar.log 2>&1
30 5 1 * * root $SCRIPTS/probar-restauracion.sh >> $LOGS/restauracion.log 2>&1
EOF
chmod 644 /etc/cron.d/cofe-app
systemctl restart cron 2>/dev/null || true

# --- 6. Primera versión ----------------------------------------------------------------
if docker image inspect cofe-backend:actual >/dev/null 2>&1; then
  log "Ya hay una versión puesta ($(version_actual)). Para actualizar: probar-version.sh y actualizar.sh"
  dc up -d
else
  ver="$(date +%Y%m%d-%H%M)"
  if git -C "$RAIZ" rev-parse --short HEAD >/dev/null 2>&1; then ver="$ver-$(git -C "$RAIZ" rev-parse --short HEAD)"; fi
  log "Armando la primera versión ($ver): tarda unos minutos…"
  docker build -t "cofe-backend:$ver" "$RAIZ/backend"
  docker build -t "cofe-web:$ver" "$RAIZ/frontend"
  docker tag "cofe-backend:$ver" cofe-backend:actual
  docker tag "cofe-web:$ver" cofe-web:actual
  echo "$ver" > "$ESTADO/version-actual"
  echo "$(date '+%Y-%m-%d %H:%M') $ver anterior=ninguna base_antes=V copia=ninguna" >> "$ESTADO/historial"
  dc up -d
fi

log "Esperando que arranque (la primera vez crea la base: 1 o 2 minutos)…"
esperar_salud prod 400 || falla "el backend no respondió. Mirá: cd $DEPLOY_DIR && docker compose logs backend"
"$SCRIPTS/backup.sh" primera || log "(la primera copia de seguridad falló: revisá BACKUP_DESTINO)"

cat <<EOF

  ✔ COFFE_APP está andando en https://$dominio
    (el certificado HTTPS puede tardar un minuto la primera vez; se renueva solo)

  Falta, si no lo hiciste (docs/SERVIDOR.md):
    - Copias afuera del servidor: sudo rclone config  y  BACKUP_DESTINO en deploy/.env
    - Avisos al celular: app ntfy  y  AVISOS_NTFY_TEMA en deploy/.env
    - Monitor externo gratis (UptimeRobot) a https://$dominio/api/v1/actuator/health

  Para ver cómo está todo:  sudo bash deploy/scripts/estado.sh

EOF
