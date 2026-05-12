package io.github.rygel.outerstellar.platform.security

import org.slf4j.LoggerFactory

class AppleOAuthProvider(
    private val teamId: String,
    private val clientId: String,
    private val keyId: String,
    private val privateKeyPem: String,
) : OAuthProvider {

    private val logger = LoggerFactory.getLogger(AppleOAuthProvider::class.java)

    companion object {
        private const val APPLE_AUTH_ENDPOINT = "https://appleid.apple.com/auth/authorize"
        private const val APPLE_TOKEN_ENDPOINT = "https://appleid.apple.com/auth/token"
    }

    override val name: String = "apple"

    override fun authorizationUrl(state: String, redirectUri: String): String {
        if (clientId.isBlank()) {
            logger.warn(
                "AppleOAuthProvider is not configured — using stub authorization URL. " +
                    "Set teamId, clientId, keyId, and privateKeyPem to enable real Sign in with Apple."
            )
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
        if (clientId.isBlank()) {
            throw OAuthException(
                "Sign in with Apple is not yet configured. " +
                    "Provide Apple Developer credentials in AppleOAuthConfig."
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
