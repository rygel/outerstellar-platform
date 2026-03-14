# Multi-stage build for outerstellar-starter
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Install Node.js for Tailwind CSS build
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# Copy POM files first for dependency caching
COPY pom.xml .
COPY core/pom.xml core/
COPY security/pom.xml security/
COPY persistence-jooq/pom.xml persistence-jooq/
COPY persistence-jdbi/pom.xml persistence-jdbi/
COPY api-client/pom.xml api-client/
COPY web/pom.xml web/
COPY desktop/pom.xml desktop/
COPY seed/pom.xml seed/

# Copy npm config for CSS build
COPY package.json package-lock.json* ./

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl web -am -DskipTests -Denforcer.skip=true || true
RUN npm install || true

# Copy source code
COPY . .

# Build the project, skip tests and quality checks
RUN mvn package -pl web -am -DskipTests -Denforcer.skip=true \
    -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true \
    -Dspotbugs.skip=true -Ddetekt.skip=true -Djacoco.skip=true

# Copy runtime dependencies into a lib directory
RUN mvn dependency:copy-dependencies -pl web -DincludeScope=runtime \
    -DoutputDirectory=web/target/lib -DskipTests -Denforcer.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy application jar and dependencies
COPY --from=builder /app/web/target/web-*.jar app.jar
COPY --from=builder /app/web/target/lib/ lib/

EXPOSE 8080
ENV APP_PROFILE=prod
ENV SESSIONCOOKIESECURE=true

ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "dev.outerstellar.starter.MainKt"]
