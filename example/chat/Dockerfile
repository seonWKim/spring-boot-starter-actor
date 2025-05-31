# Use OpenJDK 17 as base image
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Copy the application jar file
COPY build/libs/*.jar app.jar

# Create environment variables for the configurable properties with default values
ENV PEKKO_HOSTNAME=127.0.0.1 \
    PEKKO_PORT=2551 \
    SEED_NODES="pekko://spring-pekko-example@127.0.0.1:2551,pekko://spring-pekko-example@127.0.0.1:2552,pekko://spring-pekko-example@127.0.0.1:2553" \
    SERVER_PORT=8080 \
    # JVM options optimized for containers
    JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -XX:MaxRAMPercentage=75 \
               -XX:+HeapDumpOnOutOfMemoryError \
               --add-opens java.base/java.lang=ALL-UNNAMED \
               --add-opens java.base/java.io=ALL-UNNAMED \
               --add-opens java.base/java.util=ALL-UNNAMED \
               --add-opens java.base/java.util.concurrent=ALL-UNNAMED"

# Create an entrypoint script to handle the configuration
RUN echo '#!/bin/sh \n\
exec java $JAVA_OPTS -jar app.jar \
--spring.actor.pekko.remote.artery.canonical.hostname=${PEKKO_HOSTNAME} \
--spring.actor.pekko.remote.artery.canonical.port=${PEKKO_PORT} \
--spring.actor.pekko.cluster.seed-nodes=${SEED_NODES} \
--server.port=${SERVER_PORT}' > /app/entrypoint.sh && \
chmod +x /app/entrypoint.sh

# Set non-root user for security
RUN addgroup --system --gid 1001 appuser && \
    adduser --system --uid 1001 --gid 1001 appuser && \
    chown -R appuser:appuser /app

USER appuser

# Set the entrypoint
ENTRYPOINT ["/app/entrypoint.sh"]
