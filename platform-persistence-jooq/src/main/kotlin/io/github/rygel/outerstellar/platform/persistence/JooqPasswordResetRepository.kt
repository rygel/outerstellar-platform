package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.security.PasswordResetRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqPasswordResetRepository(private val dsl: DSLContext) : PasswordResetRepository {
    private val table = DSL.table("plt_password_reset_tokens")
    private val userId = DSL.field("user_id", SQLDataType.UUID)
    private val token = DSL.field("token", SQLDataType.VARCHAR)
    private val expiresAt = DSL.field("expires_at", SQLDataType.LOCALDATETIME)
    private val used = DSL.field("used", SQLDataType.BOOLEAN)

    override fun save(token: PasswordResetToken) {
        dsl.insertInto(table)
            .set(userId, token.userId)
            .set(this.token, token.token)
            .set(expiresAt, LocalDateTime.ofInstant(token.expiresAt, ZoneOffset.UTC))
            .set(used, token.used)
            .execute()
    }

    override fun findByTokenHash(tokenHash: String): PasswordResetToken? {
        return dsl.select(userId, this.token, expiresAt, used)
            .from(table)
            .where(this.token.eq(tokenHash))
            .fetchOne()
            ?.let {
                PasswordResetToken(
                    userId = it.get(userId)!!,
                    token = it.get(this.token)!!,
                    expiresAt = it.get(expiresAt)!!.toInstant(ZoneOffset.UTC),
                    used = it.get(used)!!,
                )
            }
    }

    override fun markUsedByHash(tokenHash: String) {
        dsl.update(table).set(used, true).where(this.token.eq(tokenHash)).execute()
    }
}
