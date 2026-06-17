package io.github.rygel.outerstellar.platform.security

import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.qr.ZxingPngQrGenerator
import dev.samstevens.totp.recovery.RecoveryCodeGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.util.Utils

/**
 * Hashes and verifies TOTP backup codes. Backup codes are an equally critical credential as the main password (a single
 * code bypasses TOTP entirely), so they are stored with the same slow, salted KDF used for passwords rather than a fast
 * unsalted digest. Verification goes through [PasswordEncoder.matches], whose underlying
 * [org.mindrot.jbcrypt.BCrypt.checkpw] is constant-time.
 */
class TOTPService(private val backupCodeEncoder: PasswordEncoder) {

    private val secretGenerator = DefaultSecretGenerator(32)
    private val codeVerifier =
        DefaultCodeVerifier(
                dev.samstevens.totp.code.DefaultCodeGenerator(HashingAlgorithm.SHA1, 6),
                SystemTimeProvider(),
            )
            .apply {
                setTimePeriod(30)
                setAllowedTimePeriodDiscrepancy(1)
            }
    private val qrGenerator = ZxingPngQrGenerator()
    private val recoveryCodeGenerator = RecoveryCodeGenerator()

    fun generateSecret(): String = secretGenerator.generate()

    fun generateQrDataUri(secret: String, email: String): String {
        val data =
            QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer("Outerstellar")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
        val imageData = qrGenerator.generate(data)
        return Utils.getDataUriForImage(imageData, qrGenerator.imageMimeType)
    }

    fun verifyCode(secret: String, code: String): Boolean = codeVerifier.isValidCode(secret, code)

    fun generateBackupCodes(): Pair<List<String>, String> {
        val codes = recoveryCodeGenerator.generateCodes(16).toList()
        val hashed = codes.map { backupCodeEncoder.encode(it) }
        return codes to serializeJson(hashed)
    }

    fun verifyBackupCode(code: String, hashedCodesJson: String): String? {
        val hashedCodes = parseJsonList(hashedCodesJson).toMutableList()
        val idx = hashedCodes.indexOfFirst { backupCodeEncoder.matches(code, it) }
        if (idx == -1) return null
        hashedCodes.removeAt(idx)
        return if (hashedCodes.isEmpty()) "" else serializeJson(hashedCodes)
    }

    private fun serializeJson(list: List<String>): String = list.joinToString(",", "[", "]") { "\"$it\"" }

    private fun parseJsonList(json: String): List<String> =
        json.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
}
