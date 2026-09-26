# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies separately from source so code-only changes don't
# re-download the world.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl is needed for the HEALTHCHECK below; nginx fronts the REST API and
# the Socket.IO server on one public port so the client (and the host's
# platform, e.g. Render/Railway) only ever sees a single port; gettext-base
# provides envsubst, used to inject the host-assigned $PORT into nginx.conf
# at container start.
RUN apt-get update && apt-get install -y --no-install-recommends curl nginx gettext-base \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd -r mindcart && useradd -r -g mindcart mindcart
COPY --from=build /app/target/mindcart-backend.jar app.jar
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh \
    && chown -R mindcart:mindcart /app /etc/nginx /var/log/nginx /var/lib/nginx
USER mindcart

# Only the nginx-facing port is public now; 4000/4001 are internal-only,
# fixed by start.sh regardless of what $PORT the platform injects.
EXPOSE 4000

# Checks the Spring app directly on its internal port (bypassing nginx),
# which is what container orchestrators care about: the JVM is up.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:4000/health || exit 1

ENTRYPOINT ["/app/start.sh"]
