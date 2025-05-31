# Use OpenJDK 17 as base image
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Copy the application jar file
COPY build/libs/*.jar app.jar

# Copy the entrypoint script
COPY <<'EOF' /app/entrypoint.sh
#!/bin/sh

# Fail fast if any required environment variables are missing or empty
: "${PEKKO_HOSTNAME:?PEKKO_HOSTNAME is not set}"
: "${PEKKO_PORT:?PEKKO_PORT is not set}"
: "${SEED_NODES:?SEED_NODES is not set}"
: "${SERVER_PORT:?SERVER_PORT is not set}"

# Optional: JVM tuning defaults
: "${JAVA_OPTS:=-XX:+UseContainerSupport -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED}"

echo "Starting app with:"
echo "  PEKKO_HOSTNAME=${PEKKO_HOSTNAME}"
echo "  PEKKO_PORT=${PEKKO_PORT}"
echo "  SEED_NODES=${SEED_NODES}"
echo "  SERVER_PORT=${SERVER_PORT}"

exec java $JAVA_OPTS -jar app.jar \
  --spring.actor.pekko.remote.artery.canonical.hostname=${PEKKO_HOSTNAME} \
  --spring.actor.pekko.remote.artery.canonical.port=${PEKKO_PORT} \
  --spring.actor.pekko.cluster.seed-nodes=${SEED_NODES} \
  --server.port=${SERVER_PORT}
EOF

RUN chmod +x /app/entrypoint.sh

# Set non-root user for security
RUN addgroup --system --gid 1001 appuser && \
    adduser --system --uid 1001 --gid 1001 appuser && \
    chown -R appuser:appuser /app

USER appuser

# Set the entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
