# Multi-stage build for outerstellar-platform
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Install Node.js for Tailwind CSS build
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

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
RUN mvn dependency:go-offline -pl platform-web -am -DskipTests -Denforcer.skip=true || true
RUN npm ci --no-audit --no-fund

# Copy source code
COPY . .

# Build the project, skip tests and quality checks
RUN mvn package -pl platform-web -am -DskipTests -Denforcer.skip=true \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true \
    -Dspotbugs.skip=true -Ddetekt.skip=true -Djacoco.skip=true

# Copy runtime dependencies into a lib directory
RUN mvn dependency:copy-dependencies -pl platform-web -DincludeScope=runtime \
    -DoutputDirectory=platform-web/target/lib -DskipTests -Denforcer.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy application jar and dependencies
COPY --from=builder /app/platform-web/target/platform-web-*.jar app.jar
COPY --from=builder /app/platform-web/target/lib/ lib/

EXPOSE 8080
ENV APP_PROFILE=prod
ENV SESSIONCOOKIESECURE=true

ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "dev.outerstellar.platform.MainKt"]
