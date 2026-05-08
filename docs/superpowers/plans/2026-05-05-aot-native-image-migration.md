# AOT Native Image Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all reflection-based dependencies with AOT-compatible alternatives so the platform-web module can compile to a GraalVM native image.

**Architecture:** Six independent dependency swaps, ordered from lowest risk to highest. Each task produces a fully working build — the project never enters a broken state. All changes target both the main tree and the `pr-security/` mirror.

**Tech Stack:** Kotlin 2.3.10, Maven multi-module, http4k 6.45.1.0, GraalVM native-image plugin 0.10.5

---

## File Structure

### New files
- `platform-core/src/main/kotlin/.../validation/SyncValidation.kt` — replaces Konform validation

### Modified files (per task)

**Task 1 (Konform removal):**
- `pom.xml` — remove `konform.version` property and managed dependency
- `platform-core/pom.xml` — remove `konform-jvm` dependency
- `platform-core/src/main/kotlin/.../sync/SyncModels.kt` — replace Konform with `require()` checks
- `platform-core/src/main/kotlin/.../service/MessageService.kt` — update validation consumption

**Task 2 (JDBI KotlinPlugin removal):**
- `platform-persistence-jdbi/pom.xml` — remove `jdbi3-kotlin` dependency
- `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt` — remove `installPlugin(KotlinPlugin())`

**Task 3 (Simple Java Mail → Angus Mail):**
- `pom.xml` — replace `simplejavamail.version` with `angus-mail.version` + `jakarta.mail-api.version`
- `platform-core/pom.xml` — swap `simple-java-mail` for `jakarta.mail-api` + `angus-mail`
- `platform-core/src/main/kotlin/.../service/SmtpEmailService.kt` — rewrite using Jakarta Mail API

**Task 4 (Hoplite → kotlinx.serialization):**
- `pom.xml` — remove `hoplite.version`, add `kotlinx-serialization.version`, add kotlinx serialization plugin
- `platform-core/pom.xml` — swap hoplite for kotlinx-serialization-json + yaml
- `platform-desktop/pom.xml` — swap hoplite for kotlinx-serialization-json + yaml
- `platform-core/src/main/kotlin/.../AppConfig.kt` — rewrite config loading
- `platform-desktop/src/main/kotlin/.../SwingAppConfig.kt` — rewrite config loading
- `platform-core/src/main/resources/application.yaml` — keep as-is (kotlinx-serialization reads YAML)

**Task 5 (Jackson → kotlinx.serialization via http4k-format-moshi):**
- `pom.xml` — replace `jackson.version` and `jackson-bom` with Moshi or kotlinx-serialization versions
- `platform-core/pom.xml` — swap `http4k-format-jackson` + `jackson-module-kotlin` for `http4k-format-kotlinx-serialization`
- `platform-web/pom.xml` — swap `http4k-format-jackson` for `http4k-format-kotlinx-serialization`
- `platform-persistence-jooq/pom.xml` — swap `http4k-format-jackson`
- `platform-persistence-jdbi/pom.xml` — swap `http4k-format-jackson`
- `platform-sync-client/pom.xml` — swap `http4k-format-jackson`
- All production files using `Jackson.auto`, `Jackson.asA()`, `Jackson.asFormatString()`, `Jackson.asJsonObject()`
- All DTO classes — add `@Serializable` annotations
- All test files using Jackson lenses

**Task 6 (Koin → koin-annotations + KSP):**
- `pom.xml` — add `koin-annotations.version`, `ksp.version`, KSP Maven plugin
- All module POMs with koin — add `koin-annotations` dependency + KSP generated sources
- All Koin module files — add `@Module` / `@Single` annotations
- All `KoinComponent` objects — verify generated code works

---

## Task 1: Remove Konform (trivial — 4 validation rules)

**Why first:** Zero risk. Konform is used for exactly 4 `minLength(1)` checks. Removing it eliminates `kotlin-reflect` from one source and removes a dependency that isn't pulling its weight.

**Files:**
- Modify: `pom.xml:98` — remove `konform.version` property
- Modify: `pom.xml:482-486` — remove managed dependency
- Modify: `platform-core/pom.xml` — remove `konform-jvm` dependency
- Modify: `platform-core/src/main/kotlin/.../sync/SyncModels.kt` — replace validation
- Modify: `platform-core/src/main/kotlin/.../service/MessageService.kt` — update import

- [ ] **Step 1: Add failing test for new validation**

Add a test in `platform-core` that validates blank `syncId`, `author`, `content` throw `ValidationException`:

```kotlin
package io.github.rygel.outerstellar.platform.sync

import io.github.rygel.outerstellar.platform.service.ValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SyncValidationTest {
    @Test
    fun `rejects blank syncId`() {
        assertThrows<ValidationException> {
            SyncPushRequest.validate(SyncPushRequest(listOf(SyncMessage("", "author", "content", 0L))))
        }
    }

    @Test
    fun `rejects blank author`() {
        assertThrows<ValidationException> {
            SyncPushRequest.validate(SyncPushRequest(listOf(SyncMessage("id", "", "content", 0L))))
        }
    }

    @Test
    fun `rejects blank content`() {
        assertThrows<ValidationException> {
            SyncPushRequest.validate(SyncPushRequest(listOf(SyncMessage("id", "author", "", 0L))))
        }
    }

    @Test
    fun `accepts valid message`() {
        SyncPushRequest.validate(SyncPushRequest(listOf(SyncMessage("id", "author", "content", 0L))))
    }

    @Test
    fun `rejects blank contact syncId`() {
        assertThrows<ValidationException> {
            SyncPushContactRequest.validate(SyncPushContactRequest(listOf(SyncContact("", "name", emptyList(), emptyList(), emptyList(), "", "", "", 0L))))
        }
    }

    @Test
    fun `rejects blank contact name`() {
        assertThrows<ValidationException> {
            SyncPushContactRequest.validate(SyncPushContactRequest(listOf(SyncContact("id", "", emptyList(), emptyList(), emptyList(), "", "", "", 0L))))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-core test -Dtest=SyncValidationTest -pl platform-core`
Expected: FAIL — `SyncPushRequest.validate` still uses Konform but test imports `ValidationException` from the right package.

- [ ] **Step 3: Replace Konform validation in SyncModels.kt**

Replace the entire file content. Remove Konform imports, add inline `validate` functions:

```kotlin
package io.github.rygel.outerstellar.platform.sync

class ValidationException(message: String) : RuntimeException(message)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncMessage(
    val syncId: String,
    val author: String,
    val content: String,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false,
) {
    companion object {
        fun validate(msg: SyncMessage): SyncMessage {
            require(msg.syncId.isNotBlank()) { "syncId must not be blank" }
            require(msg.author.isNotBlank()) { "author must not be blank" }
            require(msg.content.isNotBlank()) { "content must not be blank" }
            return msg
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushRequest(val messages: List<SyncMessage> = emptyList()) {
    companion object {
        fun validate(request: SyncPushRequest): SyncPushRequest {
            request.messages.forEach { validate(it) }
            return request
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncConflict(val syncId: String, val reason: String, val serverMessage: SyncMessage? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushResponse(val appliedCount: Int = 0, val conflicts: List<SyncConflict> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPullResponse(val messages: List<SyncMessage> = emptyList(), val serverTimestamp: Long = 0)

data class SyncStats(val pushedCount: Int = 0, val pulledCount: Int = 0, val conflictCount: Int = 0)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncContact(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val socialMedia: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false,
) {
    companion object {
        fun validate(contact: SyncContact): SyncContact {
            require(contact.syncId.isNotBlank()) { "syncId must not be blank" }
            require(contact.name.isNotBlank()) { "name must not be blank" }
            return contact
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushContactRequest(val contacts: List<SyncContact> = emptyList()) {
    companion object {
        fun validate(request: SyncPushContactRequest): SyncPushContactRequest {
            request.contacts.forEach { validate(it) }
            return request
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncContactConflict(val syncId: String, val reason: String, val serverContact: SyncContact? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushContactResponse(val appliedCount: Int = 0, val conflicts: List<SyncContactConflict> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPullContactResponse(val contacts: List<SyncContact> = emptyList(), val serverTimestamp: Long = 0)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonIgnoreProperties(val ignoreUnknown: Boolean = false)
```

Note: `ValidationException` is defined here for now. The consumption site in `MessageService` must be updated to catch `IllegalArgumentException` (from `require`) or we keep our own `ValidationException`. The simplest approach: keep the `ValidationException` class in this file and wrap `require` to throw it.

**Revised validate helpers** (throw our own `ValidationException`):

```kotlin
class ValidationException(message: String) : RuntimeException(message)

private fun fail(message: String): Nothing = throw ValidationException(message)
```

Then in each `validate` function, replace `require(x) { "msg" }` with:
```kotlin
if (msg.syncId.isBlank()) fail("syncId must not be blank")
```

- [ ] **Step 4: Update MessageService.kt consumption site**

In `platform-core/src/main/kotlin/.../service/MessageService.kt`:
- Remove `import io.konform.validation.Invalid`
- Remove `import io.konform.validation.Validation` (if present)
- The existing code pattern is:
  ```kotlin
  val validationResult = SyncPushRequest.validate(request)
  if (validationResult is Invalid) { throw ValidationException(...) }
  ```
- Replace with:
  ```kotlin
  SyncPushRequest.validate(request)
  ```
  The new `validate()` already throws `ValidationException` on failure.

- [ ] **Step 5: Remove Konform from POMs**

In root `pom.xml`:
- Remove `<konform.version>0.11.1</konform.version>` (line 98)
- Remove the managed dependency block (lines 482-486):
  ```xml
  <dependency>
      <groupId>io.konform</groupId>
      <artifactId>konform-jvm</artifactId>
      <version>${konform.version}</version>
  </dependency>
  ```

In `platform-core/pom.xml`:
- Remove the `konform-jvm` dependency.

- [ ] **Step 6: Run tests**

Run: `mvn -pl platform-core test`
Expected: All tests pass, including the new `SyncValidationTest`.

- [ ] **Step 7: Run full build**

Run: `mvn clean install -T 4 -DskipTests && mvn -pl platform-web test -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: replace Konform with inline require() validation"
```

---

## Task 2: Remove JDBI KotlinPlugin (trivial — 1 line + 1 dep)

**Why second:** Zero risk. The JDBI module only uses `KotlinPlugin` for reflection-based mapping that it never actually uses — all mappers are lambda-based. Removing it makes the JDBI module AOT-compatible.

**Files:**
- Modify: `platform-persistence-jdbi/pom.xml` — remove `jdbi3-kotlin` dependency
- Modify: `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt` — remove `installPlugin(KotlinPlugin())`
- Modify: `pom.xml:308-312` — optionally remove `jdbi3-kotlin` managed dependency

- [ ] **Step 1: Remove KotlinPlugin from PersistenceModule.kt**

In `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`:
- Remove `import org.jdbi.v3.core.kotlin.KotlinPlugin`
- Remove `.installPlugin(KotlinPlugin())` from both `Jdbi.create(...)` calls
- The Jdbi creation becomes just: `Jdbi.create(dataSource)`

- [ ] **Step 2: Remove jdbi3-kotlin dependency**

In `platform-persistence-jdbi/pom.xml`:
- Remove the `jdbi3-kotlin` dependency

In root `pom.xml`:
- Remove the managed `jdbi3-kotlin` entry (lines 308-312)

- [ ] **Step 3: Run JDBI module tests**

Run: `mvn -pl platform-persistence-jdbi clean test`
Expected: All JDBI tests pass — the lambda-based mappers don't need KotlinPlugin.

- [ ] **Step 4: Run full build**

Run: `mvn clean install -T 4 -DskipTests && mvn -pl platform-web test -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: remove JDBI KotlinPlugin for AOT compatibility"
```

---

## Task 3: Replace Simple Java Mail with Angus Mail (low risk — 1 file)

**Why third:** Single file change. Angus Mail has built-in GraalVM native-image support. Eliminates the Simple Java Mail abstraction layer and its SPI overhead.

**Files:**
- Modify: `pom.xml:97` — replace `simplejavamail.version` with `angus.mail.version` + `jakarta.mail.api.version`
- Modify: `pom.xml:471-481` — replace managed dependency
- Modify: `platform-core/pom.xml` — swap dependencies
- Modify: `platform-core/src/main/kotlin/.../service/SmtpEmailService.kt` — rewrite

- [ ] **Step 1: Write failing test for SmtpEmailService**

Create a test that verifies the SmtpEmailService can be constructed and attempts to send (we'll mock the Transport):

```kotlin
package io.github.rygel.outerstellar.platform.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

class SmtpEmailServiceTest {
    @Test
    fun `can be constructed with config`() {
        val service = SmtpEmailService(
            SmtpConfig(host = "localhost", port = 25, from = "test@test.com"),
        )
        assertNotNull(service)
    }

    @Test
    fun `send throws on invalid host`() {
        val service = SmtpEmailService(
            SmtpConfig(host = "invalid.host.invalid", port = 25, from = "test@test.com"),
        )
        assertThrows<EmailDeliveryException> {
            service.send("to@test.com", "Test", "Body")
        }
    }
}
```

- [ ] **Step 2: Rewrite SmtpEmailService.kt using Jakarta Mail**

```kotlin
package io.github.rygel.outerstellar.platform.service

import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.eclipse.angus.mail.smtp.SMTPTransport
import org.slf4j.LoggerFactory
import java.util.Properties

class EmailDeliveryException(message: String, cause: Throwable) : RuntimeException(message, cause)

data class SmtpConfig(
    val host: String,
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
)

class SmtpEmailService(private val config: SmtpConfig) : EmailService {
    private val logger = LoggerFactory.getLogger(SmtpEmailService::class.java)

    private val session: Session by lazy {
        val props = Properties()
        props["mail.smtp.host"] = config.host
        props["mail.smtp.port"] = config.port.toString()
        props["mail.smtp.starttls.enable"] = config.startTls.toString()
        props["mail.smtp.ssl.trust"] = config.host
        if (config.username.isNotBlank()) {
            props["mail.smtp.auth"] = "true"
        }
        Session.getInstance(props)
    }

    override fun send(to: String, subject: String, body: String) {
        try {
            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from))
                setRecipient(jakarta.mail.Message.RecipientType.TO, InternetAddress(to))
                setSubject(subject)
                setText(body)
            }
            val transport = session.getTransport("smtp") as SMTPTransport
            transport.connect(config.host, config.username, config.password)
            transport.sendMessage(msg, msg.allRecipients)
            transport.close()
            logger.info("Email sent to {} subject='{}'", to, subject)
        } catch (e: Exception) {
            logger.warn("Failed to send email to {}: {}", to, e.message)
            throw EmailDeliveryException("Email delivery failed to $to: ${e.message}", e)
        }
    }
}
```

- [ ] **Step 3: Update POMs**

Root `pom.xml`:
- Replace `<simplejavamail.version>8.12.6</simplejavamail.version>` with:
  ```xml
  <angus.mail.version>2.0.4</angus.mail.version>
  <jakarta.mail.api.version>2.1.3</jakarta.mail.api.version>
  ```
- Replace the managed dependency (lines 471-481) with:
  ```xml
  <dependency>
      <groupId>jakarta.mail</groupId>
      <artifactId>jakarta.mail-api</artifactId>
      <version>${jakarta.mail.api.version}</version>
  </dependency>
  <dependency>
      <groupId>org.eclipse.angus</groupId>
      <artifactId>angus-mail</artifactId>
      <version>${angus.mail.version}</version>
      <scope>runtime</scope>
  </dependency>
  ```

`platform-core/pom.xml`:
- Replace `simple-java-mail` with:
  ```xml
  <dependency>
      <groupId>jakarta.mail</groupId>
      <artifactId>jakarta.mail-api</artifactId>
  </dependency>
  <dependency>
      <groupId>org.eclipse.angus</groupId>
      <artifactId>angus-mail</artifactId>
  </dependency>
  ```

- [ ] **Step 4: Run tests**

Run: `mvn -pl platform-core test -Dtest=SmtpEmailServiceTest`
Expected: Tests pass (construction test passes, send throws on invalid host).

- [ ] **Step 5: Run full build**

Run: `mvn clean install -T 4 -DskipTests && mvn -pl platform-web test -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor: replace Simple Java Mail with Angus Mail for AOT compatibility"
```

---

## Task 4: Replace Hoplite with kotlinx.serialization (low risk — 2 config files)

**Why fourth:** Two config classes, two loader sites. Hoplite uses Kotlin reflection to inspect constructors. kotlinx.serialization generates serializers at compile time via KSP, fully AOT-compatible.

**Prerequisite:** The Kotlin compiler must have the `kotlinx-serialization` plugin enabled. This requires adding the plugin to the root POM's `pluginManagement` and applying it to modules that use `@Serializable`.

**Files:**
- Modify: `pom.xml` — add serialization plugin + remove hoplite
- Modify: `platform-core/pom.xml` — swap deps, add serialization plugin
- Modify: `platform-desktop/pom.xml` — swap deps, add serialization plugin
- Modify: `platform-core/src/main/resources/application.yaml` — keep as-is
- Modify: `platform-core/src/main/kotlin/.../AppConfig.kt` — rewrite with `@Serializable`
- Modify: `platform-desktop/src/main/kotlin/.../SwingAppConfig.kt` — rewrite with `@Serializable`

- [ ] **Step 1: Add kotlinx.serialization to root POM**

In root `pom.xml` properties, add:
```xml
<kotlinx.serialization.version>1.8.1</kotlinx.serialization.version>
```

In `dependencyManagement`, replace hoplite entries with:
```xml
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-serialization-core-jvm</artifactId>
    <version>${kotlinx.serialization.version}</version>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-serialization-json-jvm</artifactId>
    <version>${kotlinx.serialization.version}</version>
</dependency>
<dependency>
    <groupId>org.jetbrains.kotlinx</groupId>
    <artifactId>kotlinx-serialization-yaml-jvm</artifactId>
    <version>${kotlinx.serialization.version}</version>
</dependency>
```

Remove hoplite managed dependencies.

In `build > pluginManagement`, add the serialization compiler plugin to the `kotlin-maven-plugin`:
```xml
<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <version>${kotlin.version}</version>
    <configuration>
        <jvmTarget>${java.version}</jvmTarget>
        <useDaemon>false</useDaemon>
        <executionStrategy>in-process</executionStrategy>
        <compilerPlugins>
            <plugin>kotlinx-serialization</plugin>
        </compilerPlugins>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-serialization-compiler-plugin-embeddable</artifactId>
            <version>${kotlin.version}</version>
        </dependency>
    </dependencies>
    <!-- keep existing executions -->
</plugin>
```

- [ ] **Step 2: Add serialization dependencies to module POMs**

In `platform-core/pom.xml`:
- Remove `hoplite-core` and `hoplite-yaml`
- Add:
  ```xml
  <dependency>
      <groupId>org.jetbrains.kotlinx</groupId>
      <artifactId>kotlinx-serialization-json-jvm</artifactId>
  </dependency>
  <dependency>
      <groupId>org.jetbrains.kotlinx</groupId>
      <artifactId>kotlinx-serialization-yaml-jvm</artifactId>
  </dependency>
  ```

Same for `platform-desktop/pom.xml`.

- [ ] **Step 3: Rewrite AppConfig.kt**

```kotlin
package io.github.rygel.outerstellar.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.yaml.Yaml
import org.koin.dsl.module
import java.io.InputStream

val configModule
    get() = module { single { AppConfig.fromEnvironment() } }

@Serializable
data class SegmentConfig(val writeKey: String = "", val enabled: Boolean = false)

@Serializable
data class JwtConfig(
    val enabled: Boolean = false,
    val secret: String = "",
    val issuer: String = "outerstellar",
    val expirySeconds: Long = 86400L,
)

@Serializable
data class EmailConfig(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
)

@Serializable
data class AppConfig(
    val version: String = "dev",
    val port: Int = 8080,
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val devDashboardEnabled: Boolean = false,
    val devMode: Boolean = false,
    val sessionCookieSecure: Boolean = false,
    val sessionTimeoutMinutes: Int = 30,
    val corsOrigins: String = "*",
    val csrfEnabled: Boolean = true,
    val segment: SegmentConfig = SegmentConfig(),
    val email: EmailConfig = EmailConfig(),
    val appBaseUrl: String = "http://localhost:8080",
    val jwt: JwtConfig = JwtConfig(),
    val cspPolicy: String =
        "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "font-src 'self'; " +
            "connect-src 'self' ws: wss:; " +
            "img-src 'self' data:;",
) {
    companion object {
        private val yaml = Yaml { ignoreUnknownKeys = true }

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val resourceStream = if (profile != "default") {
                AppConfig::class.java.getResourceAsStream("/application-$profile.yaml")
                    ?: AppConfig::class.java.getResourceAsStream("/application.yaml")
            } else {
                AppConfig::class.java.getResourceAsStream("/application.yaml")
            }

            val baseConfig = resourceStream?.use { yaml.decodeFromString(serializer(), it.bufferedReader().readText()) }
                ?: AppConfig()

            return baseConfig.withEnvironmentOverrides(environment)
        }

        private fun AppConfig.withEnvironmentOverrides(env: Map<String, String>): AppConfig {
            var config = this
            env["VERSION"]?.let { config = config.copy(version = it) }
            env["PORT"]?.let { config = config.copy(port = it.toInt()) }
            env["JDBC_URL"]?.let { config = config.copy(jdbcUrl = it) }
            env["JDBC_USER"]?.let { config = config.copy(jdbcUser = it) }
            env["JDBC_PASSWORD"]?.let { config = config.copy(jdbcPassword = it) }
            env["DEV_DASHBOARD_ENABLED"]?.let { config = config.copy(devDashboardEnabled = it.toBoolean()) }
            env["DEVMODE"]?.let { config = config.copy(devMode = it.toBoolean()) }
            env["SESSIONCOOKIESECURE"]?.let { config = config.copy(sessionCookieSecure = it.toBoolean()) }
            env["SESSIONTIMEOUTMINUTES"]?.let { config = config.copy(sessionTimeoutMinutes = it.toInt()) }
            env["CORSORIGINS"]?.let { config = config.copy(corsOrigins = it) }
            env["CSRFENABLED"]?.let { config = config.copy(csrfEnabled = it.toBoolean()) }
            env["APPBASEURL"]?.let { config = config.copy(appBaseUrl = it) }
            env["SEGMENT_WRITEKEY"]?.let { config = config.copy(segment = config.segment.copy(writeKey = it)) }
            env["SEGMENT_ENABLED"]?.let { config = config.copy(segment = config.segment.copy(enabled = it.toBoolean())) }
            env["EMAIL_ENABLED"]?.let { config = config.copy(email = config.email.copy(enabled = it.toBoolean())) }
            env["EMAIL_HOST"]?.let { config = config.copy(email = config.email.copy(host = it)) }
            env["EMAIL_PORT"]?.let { config = config.copy(email = config.email.copy(port = it.toInt())) }
            env["EMAIL_USERNAME"]?.let { config = config.copy(email = config.email.copy(username = it)) }
            env["EMAIL_PASSWORD"]?.let { config = config.copy(email = config.email.copy(password = it)) }
            env["EMAIL_FROM"]?.let { config = config.copy(email = config.email.copy(from = it)) }
            env["EMAIL_STARTTLS"]?.let { config = config.copy(email = config.email.copy(startTls = it.toBoolean())) }
            env["JWT_ENABLED"]?.let { config = config.copy(jwt = config.jwt.copy(enabled = it.toBoolean())) }
            env["JWT_SECRET"]?.let { config = config.copy(jwt = config.jwt.copy(secret = it)) }
            env["JWT_ISSUER"]?.let { config = config.copy(jwt = config.jwt.copy(issuer = it)) }
            env["JWT_EXPIRY_SECONDS"]?.let { config = config.copy(jwt = config.jwt.copy(expirySeconds = it.toLong())) }
            return config
        }
    }
}
```

**Note:** This approach reads YAML for defaults then applies environment variable overrides. This matches the Hoplite behavior where both sources were merged. The `withEnvironmentOverrides` function explicitly maps each env var to its config field, avoiding any reflection.

- [ ] **Step 4: Rewrite SwingAppConfig.kt**

Same pattern: add `@Serializable`, load YAML with `kotlinx-serialization-yaml`, apply env var overrides.

- [ ] **Step 5: Remove hoplite from root POM**

- Remove `<hoplite.version>2.9.0</hoplite.version>` property
- Remove hoplite-core and hoplite-yaml managed dependencies

- [ ] **Step 6: Run full build**

Run: `mvn clean install -T 4 -DskipTests && mvn -pl platform-web test -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: replace Hoplite with kotlinx.serialization for AOT-compatible config loading"
```

---

## Task 5: Replace Jackson with kotlinx.serialization (high risk — widest surface area)

**Why fifth:** Largest change surface — every JSON serialization/deserialization call site changes. Must come after Task 4 (kotlinx.serialization plugin is already configured). http4k provides `http4k-format-kotlinx-serialization` as a drop-in replacement for `http4k-format-jackson`.

**Key changes:**
1. Replace `http4k-format-jackson` with `http4k-format-kotlinx-serialization` in all POMs
2. Add `@Serializable` to all ~30 DTO types
3. Change all `import org.http4k.format.Jackson.auto` → `import org.http4k.format.kotlinx.serialization.auto`
4. Change all `org.http4k.format.Jackson.asA()` → `org.http4k.format.kotlinx.serialization.KotlinxSerialization.asA()`
5. Change all `org.http4k.format.Jackson.asFormatString()` → `org.http4k.format.kotlinx.serialization.KotlinxSerialization.asFormatString()`
6. Replace all `jacksonObjectMapper()` usage with `kotlinx.serialization.json.Json`
7. Remove `jackson-bom`, `jackson-module-kotlin` from root POM

**Files (production):**
- Modify: `pom.xml` — replace Jackson BOM + deps with kotlinx-serialization versions, add `http4k-format-kotlinx-serialization` managed dep
- Modify: `platform-core/pom.xml` — swap format deps
- Modify: `platform-web/pom.xml` — swap format deps
- Modify: `platform-persistence-jooq/pom.xml` — swap format dep
- Modify: `platform-persistence-jdbi/pom.xml` — swap format dep
- Modify: `platform-sync-client/pom.xml` — swap format dep
- Modify: `platform-core/.../model/AuthModels.kt` — add `@Serializable` to all 18 data classes
- Modify: `platform-core/.../sync/SyncModels.kt` — add `@Serializable` to all 10 data classes
- Modify: `platform-core/.../model/ThemeCatalog.kt` — replace Jackson with kotlinx.serialization Json
- Modify: `platform-core/.../analytics/SegmentAnalyticsService.kt` — replace Jackson
- Modify: `platform-web/.../web/ThemeCatalog.kt` — replace Jackson
- Modify: `platform-web/.../web/AuthApi.kt` — change import from Jackson → kotlinx.serialization
- Modify: `platform-web/.../web/SyncApi.kt` — change import
- Modify: `platform-web/.../web/UserAdminApi.kt` — change import
- Modify: `platform-web/.../web/NotificationApi.kt` — change import
- Modify: `platform-web/.../web/DeviceRegistrationApi.kt` — change import + replace `Jackson.asA()`
- Modify: `platform-web/.../web/WebPageFactory.kt` — replace `Jackson.asA()`
- Modify: `platform-web/.../web/Filters.kt` — replace `Jackson.asJsonObject()`
- Modify: `platform-web/.../App.kt` — replace `Jackson` references
- Modify: `platform-persistence-jooq/.../JooqMessageRepository.kt` — replace `Jackson.asFormatString()`
- Modify: `platform-persistence-jooq/.../JooqContactRepository.kt` — replace `Jackson.asFormatString()`
- Modify: `platform-persistence-jdbi/.../JdbiMessageRepository.kt` — replace `Jackson.asFormatString()`
- Modify: `platform-persistence-jdbi/.../JdbiContactRepository.kt` — replace `Jackson.asFormatString()`
- Modify: `platform-sync-client/.../SyncService.kt` — change import + all lenses
- Modify: `platform-desktop/.../PersistentBatchingAnalyticsService.kt` — replace Jackson
- Modify: `platform-web/.../NotificationApi.kt` — `NotificationDto` needs `@Serializable`

**IMPORTANT:** `http4k-format-kotlinx-serialization` uses the object `KotlinxSerialization` instead of `Jackson`. The import pattern changes from:
```kotlin
import org.http4k.format.Jackson.auto
```
to:
```kotlin
import org.http4k.format.kotlinx.serialization.auto
```

And the singleton object changes from `Jackson` to `KotlinxSerialization`. All `Body.auto<T>().toLens()` calls remain identical — only the import changes.

For `Jackson.asA(json, Type::class)` → `KotlinxSerialization.asA(json, Type::class)`
For `Jackson.asFormatString(obj)` → `KotlinxSerialization.asFormatString(obj)`
For `Jackson.asJsonObject(map)` → use `kotlinx.serialization.json.Json.encodeToJsonElement(map).toString()` or `KotlinxSerialization.asJsonObject(map)` if available.

- [ ] **Step 1: Add @Serializable to all DTO types**

In `AuthModels.kt`, add to every data class:
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(...)
@Serializable
data class RegisterRequest(...)
// ... all 18 classes
```

In `SyncModels.kt`, add `@Serializable` to all 10 data classes.

In `DeviceRegistrationApi.kt`, add `@Serializable` to `RegisterDeviceRequest` and `DeregisterDeviceRequest`.

In `NotificationApi.kt`, add `@Serializable` to `NotificationDto`.

**Important:** The `@JsonIgnoreProperties` custom annotation in `SyncModels.kt` can be removed — kotlinx.serialization ignores unknown properties by default with `Json { ignoreUnknownKeys = true }`.

- [ ] **Step 2: Swap http4k format dependency in all POMs**

Root `pom.xml`:
- Replace `http4k-format-jackson` managed dependency with:
  ```xml
  <dependency>
      <groupId>org.http4k</groupId>
      <artifactId>http4k-format-kotlinx-serialization</artifactId>
      <version>${http4k.version}</version>
  </dependency>
  ```
- Remove `jackson-bom` import and `jackson-module-kotlin` managed dependency
- Remove `<jackson.version>2.21.3</jackson.version>` property

Each module POM (`platform-core`, `platform-web`, `platform-persistence-jooq`, `platform-persistence-jdbi`, `platform-sync-client`):
- Replace `http4k-format-jackson` with `http4k-format-kotlinx-serialization`
- Remove `jackson-module-kotlin`

- [ ] **Step 3: Update all lens imports (production)**

In each file that uses `Body.auto<T>().toLens()`:
- Replace `import org.http4k.format.Jackson.auto` with `import org.http4k.format.kotlinx.serialization.auto`

Files: `AuthApi.kt`, `SyncApi.kt`, `UserAdminApi.kt`, `NotificationApi.kt`, `SyncService.kt`

- [ ] **Step 4: Replace Jackson.asA / asFormatString / asJsonObject calls**

Each file that uses `Jackson` directly:
- Replace `import org.http4k.format.Jackson` with `import org.http4k.format.kotlinx.serialization.KotlinxSerialization`
- Replace `Jackson.asA(...)` with `KotlinxSerialization.asA(...)`
- Replace `Jackson.asFormatString(...)` with `KotlinxSerialization.asFormatString(...)`
- Replace `Jackson.asJsonObject(...)` with `KotlinxSerialization.asJsonObject(...)`

Files: `MessageService.kt`, `WebPageFactory.kt`, `Filters.kt`, `App.kt`, `DeviceRegistrationApi.kt`, `JooqMessageRepository.kt`, `JooqContactRepository.kt`, `JdbiMessageRepository.kt`, `JdbiContactRepository.kt`

- [ ] **Step 5: Replace jacksonObjectMapper() usage**

`ThemeCatalog.kt` (core and web):
```kotlin
// Before:
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
private val objectMapper = jacksonObjectMapper()
objectMapper.readValue<List<ThemeDefinition>>(resourceStream)

// After:
import kotlinx.serialization.json.Json
private val json = Json { ignoreUnknownKeys = true }
json.decodeFromString<List<ThemeDefinition>>(resourceStream.bufferedReader().readText())
```

`SegmentAnalyticsService.kt`:
```kotlin
// Before:
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
private val mapper = jacksonObjectMapper()
mapper.writeValueAsString(payload)

// After:
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
private val json = Json { ignoreUnknownKeys = true }
json.encodeToString(payload)
```

**Note:** `SegmentAnalyticsService` serializes `Map<String, Any>` payloads. kotlinx.serialization can't serialize `Any` directly. Options:
- Define a `@Serializable` data class for the Segment analytics event
- Use `kotlinx.serialization.json.buildJsonObject { ... }` for ad-hoc JSON
- Keep a minimal Jackson dependency for this one use case (not recommended)

The cleanest approach: define `@Serializable data class SegmentEvent(...)` and use it.

`PersistentBatchingAnalyticsService.kt` (desktop): Same pattern as SegmentAnalyticsService.

- [ ] **Step 6: Update OpenApi3 configuration in App.kt**

```kotlin
// Before:
import org.http4k.format.Jackson
renderer = OpenApi3(ApiInfo("$appLabel API", "v1.0"), Jackson)

// After:
import org.http4k.format.kotlinx.serialization.KotlinxSerialization
renderer = OpenApi3(ApiInfo("$appLabel API", "v1.0"), KotlinxSerialization)
```

- [ ] **Step 7: Update all test files**

Every test file that uses `Body.auto<T>().toLens()` or `Jackson.asA()`:
- Change import from `Jackson.auto` to `kotlinx.serialization.auto`
- Change `Jackson` to `KotlinxSerialization`

There are ~50+ test lens definitions. All follow the same pattern.

- [ ] **Step 8: Run full build**

Run: `mvn clean install -T 4 -DskipTests`
Expected: Compiles successfully.

Then: `mvn -pl platform-web test -T 4`
Expected: All 538 tests pass.

Then: `mvn -pl platform-persistence-jdbi test -T 4`
Expected: All JDBI tests pass.

Then: `mvn -pl platform-sync-client test -T 4`
Expected: All sync client tests pass.

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "refactor: replace Jackson with kotlinx.serialization for AOT-compatible JSON"
```

---

## Task 6: Add koin-annotations + KSP (medium risk — DI code gen)

**Why last:** Largest structural change to the DI system. koin-annotations generates `Module` definitions at compile time via KSP, eliminating runtime reflection from Koin. The existing `module { }` DSL code continues to work — annotations are additive. We add `@Module` and `@Single` annotations, then KSP generates the default implementations.

**How koin-annotations works:**
1. Annotate Koin module classes with `@Module`
2. Annotate `single`-providing functions with `@Single`
3. KSP generates Kotlin code that replaces the runtime DSL
4. At runtime, the generated code creates modules without reflection

**IMPORTANT:** For Maven, koin-annotations requires the `ksp` Maven plugin (from `com.google.devtools.ksp`). This is not yet as mature as the Gradle integration. Alternative approach: **keep the existing `module { }` DSL but explicitly specify all type parameters**, which eliminates the need for `kotlin-reflect` in Koin 4.x.

**Revised approach — eliminate kotlin-reflect without koin-annotations:**

Koin 4.x only needs `kotlin-reflect` when type parameters cannot be resolved at compile time. The `by inject()` pattern is the primary culprit. We can:

1. Replace all `by inject<T>()` with explicit `get<T>()` calls
2. This makes `koin-core-jvm` work without `kotlin-reflect` on the classpath
3. No KSP plugin needed, no annotation processing, no generated code

This is simpler and more reliable than the KSP approach in a Maven build.

**Files:**
- Modify: All `KoinComponent` objects — replace `by inject()` with `get()`
- Modify: Root `pom.xml` — add `koin-annotations` managed dependency + KSP plugin (if using annotations approach)
- Modify: All module files — add `@Module` / `@Single` annotations (if using annotations approach)
- Modify: All Koin module test files — update as needed

- [ ] **Step 1: Replace `by inject()` with `get()` in Main.kt**

```kotlin
// Before (platform-web/.../Main.kt):
object MainComponent : KoinComponent {
    val config: AppConfig by inject()
    val repository: MessageRepository by inject()
    // ...

// After:
object MainComponent : KoinComponent {
    val config: AppConfig = get()
    val repository: MessageRepository = get()
    // ...
```

Apply to all 8 fields in `MainComponent`.

- [ ] **Step 2: Replace `by inject()` in all other KoinComponent objects**

Same pattern for:
- `SeedComponent` in `platform-seed/.../SeedData.kt` (4 fields)
- `DesktopComponent` in `platform-desktop/.../SwingSyncApp.kt` (4 fields)

- [ ] **Step 3: Replace `by inject()` in test files**

Search for all `by inject()` in test files and replace with `get()`.

- [ ] **Step 4: Exclude kotlin-reflect from koin-core-jvm**

In root `pom.xml`, update the `koin-core-jvm` managed dependency to exclude `kotlin-reflect`:

```xml
<dependency>
    <groupId>io.insert-koin</groupId>
    <artifactId>koin-core-jvm</artifactId>
    <version>${koin.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-reflect</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Also verify no other dependency pulls in `kotlin-reflect` transitively (Jackson previously did — now removed).

- [ ] **Step 5: Verify no kotlin-reflect on classpath**

Run: `mvn -pl platform-web dependency:tree -Dincludes="org.jetbrains.kotlin:kotlin-reflect"`
Expected: No output (kotlin-reflect not in tree).

- [ ] **Step 6: Run full build**

Run: `mvn clean install -T 4 -DskipTests && mvn -pl platform-web test -T 4`
Expected: BUILD SUCCESS — Koin works without kotlin-reflect when all types are explicit.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: eliminate kotlin-reflect from Koin by using explicit get() instead of by inject()"
```

---

## Verification Task: Full Native Image Build

After all 6 tasks are complete:

- [ ] **Step 1: Verify kotlin-reflect is fully eliminated**

```bash
mvn dependency:tree -Dincludes="org.jetbrains.kotlin:kotlin-reflect"
```
Expected: Empty — no module has kotlin-reflect.

- [ ] **Step 2: Run full test suite**

```bash
mvn clean install -T 4
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Test native image compilation (requires GraalVM JDK)**

```bash
mvn -pl platform-web -Pnative package
```
Expected: Native image `outerstellar-web` compiled successfully.

- [ ] **Step 4: Run native image smoke test**

```bash
./platform-web/target/outerstellar-web
```
Expected: Application starts, health check responds on configured port.

- [ ] **Step 5: Commit final state**

```bash
git add -A && git commit -m "chore: complete AOT native image migration"
```
