package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.PasswordResetToken
import dev.outerstellar.starter.security.PasswordResetRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqPasswordResetRepository(private val dsl: DSLContext) : PasswordResetRepository {
    private val table = DSL.table("password_reset_tokens")
    private val userId = DSL.field("user_id", SQLDataType.UUID)
    private val token = DSL.field("token", SQLDataType.VARCHAR)
    private val expiresAt = DSL.field("expires_at", SQLDataType.LOCALDATETIME)
    private val used = DSL.field("used", SQLDataType.BOOLEAN)
    private val createdAt = DSL.field("created_at", SQLDataType.LOCALDATETIME)

    override fun save(resetToken: PasswordResetToken) {
        dsl.insertInto(table)
            .set(userId, resetToken.userId)
            .set(token, resetToken.token)
            .set(expiresAt, LocalDateTime.ofInstant(resetToken.expiresAt, ZoneOffset.UTC))
            .set(used, resetToken.used)
            .execute()
    }

    override fun findByToken(tokenValue: String): PasswordResetToken? {
        return dsl.select(userId, token, expiresAt, used)
            .from(table)
            .where(token.eq(tokenValue))
            .fetchOne()
            ?.let {
                PasswordResetToken(
                    userId = it.get(userId)!!,
                    token = it.get(token)!!,
                    expiresAt = it.get(expiresAt)!!.toInstant(ZoneOffset.UTC),
                    used = it.get(used)!!,
                )
            }
    }

    override fun markUsed(tokenValue: String) {
        dsl.update(table).set(used, true).where(token.eq(tokenValue)).execute()
    }
}
