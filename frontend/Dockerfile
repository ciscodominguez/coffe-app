# COFFE_APP — imagen del sitio web: la app Angular servida por Caddy, que
# además pone el HTTPS (y lo renueva solo) y le pasa /api/ al backend.
# La arma deploy/scripts/probar-version.sh; no hace falta usarla a mano.

# 1) Compilar Angular.
FROM node:22-alpine AS build
WORKDIR /src
ENV NODE_OPTIONS="--max-old-space-size=1024"
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# 2) Imagen final: Caddy con los archivos ya compilados.
#    La configuración (deploy/Caddyfile) se monta desde docker-compose.
FROM caddy:2-alpine
COPY --from=build /src/dist/frontend/browser /srv
