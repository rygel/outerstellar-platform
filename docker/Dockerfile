# Multi-stage build for outerstellar-platform
FROM maven:3.9-eclipse-temurin-21@sha256:481aad0b7042ad7a924ebd49ea5b307a049bd33d758f8613d68dcbd2904f3c8e AS builder # maven:3.9-eclipse-temurin-21
WORKDIR /app

# Install Node.js 20 via the NodeSource apt repository.
# Using apt-key + signed repo avoids piping curl output directly to bash.
RUN curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
        -o /tmp/nodesource.gpg.key && \
    gpg --dearmor < /tmp/nodesource.gpg.key > /etc/apt/keyrings/nodesource.gpg && \
    rm /tmp/nodesource.gpg.key && \
    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends nodejs && \
    rm -rf /var/lib/apt/lists/*

# Copy POM files first for dependency caching
COPY pom.xml .
COPY platform-core/pom.xml platform-core/
COPY platform-security/pom.xml platform-security/
COPY platform-persistence-jooq/pom.xml platform-persistence-jooq/
COPY platform-persistence-jdbi/pom.xml platform-persistence-jdbi/
COPY platform-sync-client/pom.xml platform-sync-client/
COPY platform-web/pom.xml platform-web/
COPY platform-desktop/pom.xml platform-desktop/
COPY platform-seed/pom.xml platform-seed/

# Copy npm config for CSS build
COPY package.json package-lock.json* ./

# Download dependencies (cached layer)
RUN (mvn dependency:go-offline -pl platform-web -am -DskipTests -Denforcer.skip=true || true) && \
    npm ci --no-audit --no-fund

# Copy source code
COPY . .

# Build the project (assembly plugin produces the fat jar)
RUN mvn package -pl platform-web -am -DskipTests -Denforcer.skip=true \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true \
    -Dspotbugs.skip=true -Ddetekt.skip=true -Djacoco.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre@sha256:a27f3be269a0a12b99ee58af1c4ebe45233d119d3926f643e3da5d298d4726dc # eclipse-temurin:21-jre
WORKDIR /app

# Copy self-contained fat jar produced by maven-shade-plugin
COPY --from=builder /app/platform-web/target/*-jar-with-dependencies.jar app.jar

EXPOSE 8080
ENV APP_PROFILE=prod
ENV SESSIONCOOKIESECURE=true

RUN groupadd -r appgroup && useradd -r -g appgroup appuser && \
    mkdir -p /app/data && chown -R appuser:appgroup /app/data
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
