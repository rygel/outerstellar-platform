<<<<<<< HEAD
# eclipse-temurin:21-jre-alpine
FROM eclipse-temurin:21-jre-alpine@sha256:6ad8ed080d9be96b61438ec3ce99388e294af216ed57356000c06070e85c5d5d

ARG VERSION
WORKDIR /app

# The web module's shaded jar (uber-jar) should be copied here.
# Assuming you run `mvn package` before building the docker image.
# Pass the version at build time: docker build --build-arg VERSION=1.4.0 ...
COPY platform-web/target/outerstellar-platform-web-${VERSION}-jar-with-dependencies.jar app.jar
=======
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# The web module's shaded jar (uber-jar) is produced at target/app.jar by maven-shade-plugin.
# Assuming you run `mvn package -pl platform-web -am` before building this image.
COPY platform-web/target/app.jar app.jar
>>>>>>> origin/main

# Add any default environment variables
ENV SERVER_PORT=8080
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none

EXPOSE 8080

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
