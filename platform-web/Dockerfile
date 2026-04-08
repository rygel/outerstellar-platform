FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# The web module's shaded jar (uber-jar) is produced at target/app.jar by maven-shade-plugin.
# Assuming you run `mvn package -pl platform-web -am` before building this image.
COPY platform-web/target/app.jar app.jar

# Add any default environment variables
ENV SERVER_PORT=8080
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none

EXPOSE 8080

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
