package dev.outerstellar.platform.security

import org.slf4j.LoggerFactory

/**
 * Sign in with Apple OAuth 2.0 provider stub.
 *
 * This implementation logs intent and returns placeholder values so the full OAuth flow can be
 * wired end-to-end without an Apple Developer account. Replace the TODO sections once you have:
 * - A valid Apple Developer Team ID
 * - A Services ID (OAuth client_id)
 * - A Key ID and the corresponding .p8 private key for generating client_secret JWTs
 *
 * Apple documentation:
 * https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api
 */
class AppleOAuthProvider(
    /** Your Apple Developer Team ID (10-character string). */
    private val teamId: String = "TODO_TEAM_ID",
    /** Services ID registered in Apple Developer portal (the OAuth client_id). */
    private val clientId: String = "TODO_CLIENT_ID",
    /** Key ID of the Sign in with Apple private key (.p8 file). */
    private val keyId: String = "TODO_KEY_ID",
    /** Contents of the .p8 private key file (PEM-encoded ES256 key). */
    private val privateKeyPem: String = "TODO_PRIVATE_KEY_PEM",
) : OAuthProvider {

    private val logger = LoggerFactory.getLogger(AppleOAuthProvider::class.java)

    companion object {
        private const val APPLE_AUTH_ENDPOINT = "https://appleid.apple.com/auth/authorize"
        private const val APPLE_TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token"
    }

    override val name: String = "apple"

    override fun authorizationUrl(state: String, redirectUri: String): String {
        if (clientId.startsWith("TODO")) {
            logger.warn(
                "AppleOAuthProvider is not configured — using stub authorization URL. " +
                    "Set teamId, clientId, keyId, and privateKeyPem to enable real Sign in with Apple."
            )
            // Return stub URL that will produce a visible error rather than silently failing.
            return "/auth/oauth/apple/not-configured"
        }

        return "$APPLE_AUTH_ENDPOINT" +
            "?response_type=code" +
            "&client_id=$clientId" +
            "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
            "&state=$state" +
            "&scope=name%20email" +
            "&response_mode=form_post"
    }

    override fun exchangeCode(code: String, state: String, redirectUri: String): OAuthUserInfo {
        if (clientId.startsWith("TODO")) {
            throw OAuthException(
                "Sign in with Apple is not yet configured. " +
                    "Provide Apple Developer credentials in AppleOAuthProvider."
            )
        }

        // TODO: Generate a client_secret JWT signed with the .p8 ES256 key, then POST to
        // APPLE_TOKEN_ENDPOINT with grant_type=authorization_code.
        // Parse the id_token JWT (RS256) to extract 'sub' and 'email' claims.
        // See:
        // https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
        throw OAuthException("AppleOAuthProvider.exchangeCode not yet implemented")
    }
}
