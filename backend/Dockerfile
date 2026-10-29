# COFFE_APP — imagen del backend (Spring Boot).
# La arma deploy/scripts/probar-version.sh; no hace falta usarla a mano.

# 1) Compilar el .jar (los tests se corren antes, en tu compu).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ENV MAVEN_OPTS="-Xmx512m"
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package \
 && cp target/*.jar /src/app.jar

# 2) Imagen final: solo Java y el .jar.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 1000 app \
 && mkdir -p /app/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER app

# Memoria de Java: 768 MB como máximo, así no le saca la RAM a MySQL.
# Si se queda sin memoria se cierra y Docker lo vuelve a levantar solo.
# Se puede cambiar con JAVA_OPTS en deploy/.env.
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:MaxMetaspaceSize=200m -XX:+ExitOnOutOfMemoryError" \
    TZ=UTC \
    UPLOADS_DIR=/app/uploads \
    PORT=3000
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD wget -qO- http://127.0.0.1:3000/api/v1/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
