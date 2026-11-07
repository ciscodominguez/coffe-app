# Poner COFFE_APP en un servidor (y que no dé problemas)

Esta guía sirve para subir la app a internet y dejarla funcionando de
forma que los cafés no tengan problemas. Casi todo está automatizado en
la carpeta `deploy/`. Vos solamente:

1. alquilás el servidor,
2. corrés **un** script,
3. completás 3 o 4 datos.

## Resumen: qué queda resuelto

| Lo que hace falta | Cómo queda |
|---|---|
| **Servidor de 2 GB de RAM como mínimo** | Spring Boot y MySQL juntos no entran cómodos en 1 GB. El instalador avisa si hay menos y agrega 2 GB de memoria de respaldo (swap) por las dudas. |
| **1. Que todo arranque solo** | Backend, MySQL y el servidor web corren con Docker Compose y `restart: unless-stopped`. Si algo se cae, o se reinicia el servidor, vuelve a levantar sin que hagas nada. Docker arranca solo al prender el servidor. |
| **2. Memoria de Java limitada** | Java usa como máximo `-Xmx768m`, así el backend no se come la RAM de MySQL. Si igual se queda sin memoria, se cierra y Docker lo levanta de nuevo, y te llega un aviso. |
| **3. HTTPS con renovación automática** | Lo hace Caddy solo: pide el certificado y lo renueva. No hay que tocar nada. |
| **4. Copias de seguridad todas las noches, afuera del servidor** | A las 4:15 se guarda la base y las imágenes de productos, y se mandan a Backblaze B2, Cloudflare R2 o Google Drive con `rclone`. Se conservan 14 días. **Una vez por mes la copia se restaura sola** en una base aparte para comprobar que sirve, y te avisa el resultado. |
| **5. Actualizaciones fuera de horario** | El script de actualización **no deja** actualizar un viernes ni con los cafés abiertos. Por defecto solo permite las noches de lunes a jueves, de 23 a 6 h. Además: exige probar antes en una copia (staging), hace una copia de seguridad antes de cada cambio en la base (V20, V21…) y guarda la versión anterior. Si la nueva no arranca, **vuelve sola** a la anterior. Para volver a mano: `scripts/volver.sh`. |
| **6. Que los logs no llenen el disco** | Cada servicio guarda como máximo 50 MB de logs y los viejos se borran solos. También quedan limitados los logs del sistema. Si el disco pasa el 80 %, te llega un aviso. |
| **7. Endpoint de salud** | `https://tu-dominio/api/v1/actuator/health` contesta `{"status":"UP"}`. Lo usan Docker, el script de actualización, la revisión cada 5 minutos y un monitor externo. |

Los avisos te llegan **al celular** con la app gratuita **ntfy** (ver el paso 5).

---

## Paso 1: alquilar el servidor y el dominio

- **Servidor (VPS):**
  - Con **Ubuntu 24.04** (o 22.04), **2 GB de RAM** o más y unos 40 GB de disco.
  - Cuesta aproximadamente **USD 5 a 15 por mes**. Sirven DigitalOcean, Vultr, Hetzner, Linode u otro parecido.
  - Elegí la región más cercana a Argentina (São Paulo, si el proveedor la tiene).
- **Dominio:**
  - Por ejemplo `coffeapp.com.ar` en NIC.ar, o uno `.com`.
  - En la configuración del dominio, creá un registro **A** que apunte `app.tudominio` (o el dominio entero) a la **IP del servidor**.
  - Ese cambio tarda unos minutos en hacer efecto.

## Paso 2: subir el código al servidor

Lo más cómodo es tener el proyecto en un repositorio **privado** de GitHub.
Desde la terminal de tu compu:

```bash
ssh root@IP-DEL-SERVIDOR            # o el usuario que te dio el proveedor
git clone https://github.com/TU-USUARIO/cofe-app.git /opt/cofe-app
cd /opt/cofe-app
```

> Sin GitHub también se puede: subí la carpeta `cofe-app` entera (sin
> `node_modules` ni `target`) a `/opt/cofe-app` con WinSCP o FileZilla.

## Paso 3: instalar (una sola vez)

```bash
sudo bash deploy/scripts/instalar.sh
```

La primera vez el script se detiene y te pide completar el archivo de
configuración:

```bash
nano deploy/.env
```

En ese archivo:

- **`DOMINIO`** (obligatorio): el dominio, sin `https://`. Por ejemplo `app.coffeapp.com.ar`.
- `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`: tu panel.
- `SUPPORT_CONTACT`.
- `BILLING_MP_ACCESS_TOKEN`: cuando quieras cobrar las suscripciones.
- El correo (`spring.mail.*`), si querés que salgan los mails.
- **Las claves** (`MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET`, `MP_TOKEN_ENCRYPTION_KEY`) ya se generaron solas, al azar. No las cambies después, y **nunca compartas el `.env`**.

Guardá el archivo (en nano: Ctrl+O, Enter, Ctrl+X) y volvé a correr:

```bash
sudo bash deploy/scripts/instalar.sh
```

El script hace todo lo siguiente:

- pone la hora de Argentina,
- crea la memoria de respaldo (swap) y configura el firewall,
- instala Docker y rclone,
- limita los logs,
- programa las tareas automáticas,
- arma la app y la pone en marcha.

Cuando termina, entrá a `https://tu-dominio`. La primera vez, el
certificado HTTPS puede tardar un minuto.

## Paso 4: copias de seguridad afuera del servidor

Si el servidor se rompe, una copia guardada en el mismo servidor no sirve
de nada. Por eso hay que configurar dónde se guardan afuera.

1. Creá una cuenta en uno de estos servicios:
   - **Backblaze B2** o **Cloudflare R2**: gratis o casi gratis para este tamaño.
   - **Google Drive**: el tuyo.
2. En el servidor, corré:
   ```bash
   sudo rclone config
   ```
   - Elegí `n` (new remote).
   - Ponele un nombre, por ejemplo `b2`.
   - Elegí el tipo de servicio y pegá las claves que te da el servicio.
   - Para Google Drive, rclone te da un link para autorizar desde tu compu.
3. En `deploy/.env` completá:
   ```
   BACKUP_DESTINO=b2:cofe-backups
   ```
   Si usaste otro nombre, va `nombre:carpeta`.
4. Probá que funcione:
   ```bash
   sudo bash deploy/scripts/backup.sh
   sudo bash deploy/scripts/probar-restauracion.sh
   ```
   El segundo comando baja la copia de afuera, la carga en una base aparte y te dice cuántos cafés, pedidos e imágenes trae.

Desde ahí corre solo:

| Cuándo | Qué |
|---|---|
| Todas las noches, 4:15 | Copia de la base y las imágenes. Se manda afuera y se conservan 14 días (en el servidor, 3). |
| Cada 5 minutos | Revisión: disco, si la app responde por internet, reinicios solos y si la última copia es de hoy. |
| El día 1 de cada mes, 5:30 | **Prueba de restauración** de la última copia de afuera. Te llega "Copia de seguridad probada: OK" o una alerta. |

## Paso 5: avisos al celular

1. Instalá la app **ntfy** en el celular. Es gratis, para Android y iPhone.
2. Tocá "+", suscribite a un **tema** con un nombre difícil de adivinar (por ejemplo `cofe-fran-8k2p9x`) y dejá el servidor `ntfy.sh`.
3. En `deploy/.env`:
   ```
   AVISOS_NTFY_TEMA=cofe-fran-8k2p9x
   ```

Te avisa en estos casos (una vez cuando empieza el problema y otra cuando se arregla):

- el disco pasó el 80 %,
- la app no responde,
- se reinició algo,
- falló una copia de seguridad,
- falló la prueba mensual,
- se actualizó o se volvió de versión.

## Paso 6: monitor externo (si se cae el servidor entero)

Si se apaga el servidor, la revisión de cada 5 minutos tampoco corre. Para
ese caso hace falta un vigilante de afuera:

1. Creá una cuenta gratis en **UptimeRobot** (o Better Stack).
2. Agregá un monitor HTTP(S) a:
   ```
   https://tu-dominio/api/v1/actuator/health
   ```
   cada 5 minutos, con aviso por mail o por la app.

## Cómo actualizar (cuando hagas cambios)

Tres reglas, que los scripts hacen cumplir:

- **nunca un viernes ni con los cafés abiertos**,
- **siempre probar antes en una copia**,
- **siempre con copia de seguridad antes**.

**1. Traer el código nuevo**, en cualquier momento:

```bash
cd /opt/cofe-app && git pull
```

**2. Probar en la copia (staging):** fuera del horario de los cafés,
porque armar la versión usa bastante procesador. Corré:

```bash
sudo bash deploy/scripts/probar-version.sh
```

El script hace esto:

- arma la versión nueva,
- copia los datos reales de ese momento a una base aparte,
- corre las migraciones nuevas sobre esa copia,
- la deja andando en `localhost:8081` del servidor.

En la copia **no salen mails ni cobros**. Para verla desde tu compu:

```bash
ssh -L 8081:localhost:8081 root@IP-DEL-SERVIDOR
```

Después abrí `http://localhost:8081` y hacé la lista de 15 minutos de
`docs/PRUEBAS-OCTUBRE-2026.md` (punto 9). Según cómo te fue:

```bash
sudo bash deploy/scripts/probar-version.sh --listo        # anda bien
sudo bash deploy/scripts/probar-version.sh --descartar    # no anda
```

**3. Ponerla:** una noche de lunes a jueves, después del cierre.

```bash
sudo bash deploy/scripts/actualizar.sh
```

El script:

1. hace una copia de seguridad ("antes-de-…"),
2. guarda la versión anterior,
3. pone la nueva,
4. espera a que el endpoint de salud diga UP.

Si la versión nueva **no arranca**, vuelve sola a la anterior, con la base
como estaba, y te avisa.

**Volver atrás a mano** (con un solo comando):

```bash
sudo bash deploy/scripts/volver.sh
```

Si la versión nueva había cambiado la base, el script te pregunta:

- `--con-base`: la base vuelve a la copia de antes de actualizar.
- `--sin-base`: los datos quedan como están.

Se guardan las 3 últimas versiones.

> El horario permitido se cambia en `deploy/.env` (`ACTUALIZAR_DIAS`,
> `ACTUALIZAR_DESDE`, `ACTUALIZAR_HASTA`). Para una emergencia de verdad
> existe `actualizar.sh --forzar-horario`, que pide confirmación.

## Comandos útiles

| Para | Comando (desde `/opt/cofe-app`) |
|---|---|
| Ver cómo está todo | `sudo bash deploy/scripts/estado.sh` |
| Ver el log del backend | `cd deploy && sudo docker compose logs --tail 200 backend` |
| Reiniciar todo | `cd deploy && sudo docker compose restart` |
| Copia de seguridad ahora | `sudo bash deploy/scripts/backup.sh` |
| Probar una copia ahora | `sudo bash deploy/scripts/probar-restauracion.sh` |
| **Emergencia:** volver los datos a una copia | `sudo bash deploy/scripts/restaurar.sh ultima` (o `--de-afuera <nombre>`) |
| Liberar disco | `sudo docker system prune` |

## Qué hay en `deploy/`

| Archivo | Para qué |
|---|---|
| `docker-compose.yml` | Los tres servicios: MySQL, backend y web (Caddy), con reinicio automático, límites de memoria y de logs. |
| `docker-compose.prueba.yml` | La copia de prueba (staging): sin mails, sin cobros, en `localhost:8081`. |
| `Caddyfile` | HTTPS automático, sirve la app y pasa `/api/` al backend. |
| `.env.example` | Modelo de la configuración (el `.env` real lo crea el instalador). |
| `scripts/` | `instalar`, `backup`, `probar-restauracion`, `restaurar`, `probar-version`, `actualizar`, `volver`, `vigilar` y `estado`. |
| `backups/`, `estado/`, `logs/` | Los crean los scripts en el servidor (no van a GitHub). |

También hay un `Dockerfile` en `backend/` y otro en `frontend/`. En
producción, la app y la API salen del **mismo dominio**, por eso
`environment.ts` usa direcciones relativas y no hay que tocar nada al
elegir el dominio.

## Cómo se probó (octubre 2026)

- **Copias, restauración, actualizar y volver:** se probaron de punta a
  punta contra MySQL real, con la base de la prueba de carga (274 cafés y
  21.835 pedidos). En lugar de Docker se usó un simulador. Se verificó:
  - la copia se hace y se manda afuera, y las viejas se borran (14 días afuera, 3 en el servidor);
  - la prueba mensual restaura en unos 5 segundos y detecta una copia dañada o incompleta;
  - la copia de prueba recibe los datos reales y corre la migración nueva sin tocar la base de los cafés;
  - `actualizar.sh` no deja actualizar sin probar antes ni fuera de horario;
  - una versión con migración se pone y se vuelve atrás con `--con-base` (la base vuelve a V19);
  - una versión rota **vuelve sola** a la anterior, con la base intacta;
  - los avisos de disco, caída, reinicio y copia vieja salen una sola vez cuando empieza el problema, y otra cuando se arregla.
- **Caddy:** la configuración se validó con Caddy 2.8 real. Con la app compilada y el backend detrás, se probó:
  - entrar,
  - las 16 llamadas a la API por el mismo dominio,
  - la **Cocina en vivo** por WebSocket (el pedido nuevo apareció sin recargar),
  - el caché correcto de archivos y la compresión.
- **Docker Compose:** la configuración se validó con `docker compose config`, tanto la normal como la de prueba.
- **No se pudo correr acá:**
  - armar las imágenes con Docker (el entorno de prueba no llega a Docker Hub),
  - el endpoint de salud con la librería Actuator (no llega a Maven Central).

  El test `HealthEndpointTest` comprueba el endpoint de salud: corre en tu compu en cuanto IntelliJ baja Actuator. La primera instalación en el servidor es la prueba final de las imágenes.
