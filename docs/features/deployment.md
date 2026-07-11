# Deployment

## Database

PostgreSQL 18 required. Start with Podman:

```bash
podman compose -f docker/podman-compose.yml up -d
```

Production: Use a managed PostgreSQL service. Set `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`.

## Build

```bash
# Build everything (skip tests)
mvn clean install -DskipTests

# Build with quality checks
mvn --% clean verify -T4 -pl !platform-desktop,!platform-desktop-javafx
```

## Run

### Web Server

```bash
# Dev mode
mvn -pl platform-web exec:java -Pruntime-dev

# Production
APP_PROFILE=prod mvn -pl platform-web exec:java -Pruntime-prod
```

Or from a fat JAR:

```bash
mvn -pl platform-web package -DskipTests
java -Xms256m -Xmx1g -jar platform-web/target/platform-web-*.jar
```

### Migrations

Run migrations separately (production):

```bash
FLYWAY_ENABLED=false java -jar platform-web-*.jar   # app starts without migrations
mvn -pl platform-persistence-jdbi -Pmigrate exec:java  # run migrations separately
```

## Docker

```bash
# Build Docker image
docker build -t outerstellar-platform -f docker/Dockerfile .

# Run
docker run -p 8080:8080 -e JDBC_URL=jdbc:postgresql://host:5432/outerstellar outerstellar-platform
```

## JVM Tuning

### Small (<256 MB heap)

```bash
java -Xms64m -Xmx256m -XX:+UseContainerSupport -XX:+UseSerialGC -jar platform-web-*.jar
```

### Standard (256 MB - 2 GB)

```bash
java -Xms256m -Xmx1g -jar platform-web-*.jar
```

### Large (2 GB+)

```bash
java -Xms2g -Xmx4g -XX:+UseParallelGC \
  -Djte.production=true -DJTE_PRELOAD_ENABLED=true \
  -jar platform-web-*.jar
```

## JVM vs Native Image

| Metric | JVM | Native Image (GraalVM) |
|---|---|---|
| Cold start | 3-5 s | <1 s |
| Idle RSS | ~150 MB | ~50 MB |
| Peak throughput | Higher (JIT) | Comparable or slightly lower |
| Best for | Steady-state servers | Auto-scaling, serverless, batch |

## Environment Variables

The application is configured entirely through env vars. See [Configuration](configuration.md) for the full reference.

Required: `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `APP_PROFILE`, and a deployment-specific `TOKEN_PEPPER` containing
at least 32 UTF-8 bytes.

`ADMIN_PASSWORD` is also required on the first boot, when the initial `admin` account does not yet exist. The server
fails before binding its HTTP port if that password is absent or does not satisfy the platform password policy.
`TOKEN_PEPPER` remains required on every boot and must be stable across replicas and restarts. Changing it invalidates
existing sessions, API keys, and outstanding password-reset tokens and prevents decryption of already-encrypted TOTP
seeds. Restore the prior value before attempting a planned key migration.

Set a stable, randomly generated `MANAGEMENT_TOKEN` of at least 32 UTF-8 bytes when a load balancer or orchestrator
probes the management endpoints through a non-loopback connection. Configure the probe with
`Authorization: Bearer <MANAGEMENT_TOKEN>`. Direct in-container loopback probes do not require the token. The provided
Compose deployment requires it because port-published probes are remote from the application's perspective.
