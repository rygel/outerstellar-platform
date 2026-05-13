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
mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
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
mvn -pl platform-persistence-jooq -Pmigrate exec:java  # run migrations separately
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

Required: `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`
Optional but recommended: `ADMIN_PASSWORD`, `APP_PROFILE`
