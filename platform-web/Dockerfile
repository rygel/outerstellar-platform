FROM eclipse-temurin:21-jre-alpine

ARG VERSION
WORKDIR /app

# The web module's shaded jar (uber-jar) should be copied here.
# Assuming you run `mvn package` before building the docker image.
# Pass the version at build time: docker build --build-arg VERSION=1.4.0 ...
COPY platform-web/target/outerstellar-platform-web-${VERSION}-jar-with-dependencies.jar app.jar

# Add any default environment variables
ENV SERVER_PORT=8080
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none

EXPOSE 8080

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
