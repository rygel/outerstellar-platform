FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# The web module's shaded jar (uber-jar) should be copied here.
# Assuming you run `mvn install` before building the docker image.
COPY web/target/web-0.1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

# Add any default environment variables
ENV SERVER_PORT=8080
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
