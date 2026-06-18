package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.persistence.OAuthUserInfo
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

        /**
         * Flip to true once exchangeCode is fully implemented (client_secret JWT signing + token endpoint POST +
         * id_token parsing). Until then the provider is consistently disabled — authorizationUrl returns the
         * not-configured stub so users are never routed through the real Apple flow only to hit the unimplemented
         * token-exchange step (issue #514).
         */
        private const val TOKEN_EXCHANGE_IMPLEMENTED = false
    }

    init {
        if (TOKEN_EXCHANGE_IMPLEMENTED.not() && clientId.isNotBlank()) {
            logger.warn(
                "Sign in with Apple is partially configured (credentials present) but the token-exchange " +
                    "step is not yet implemented — the provider is disabled until implementation is complete. " +
                    "Users will see the not-configured page rather than being routed through Apple."
            )
        }
    }

    override val name: String = "apple"

    override fun authorizationUrl(state: String, redirectUri: String): String {
        // Consistently disabled until token exchange is implemented (issue #514): previously this returned
        // a real Apple URL when clientId was set, routing users through OAuth only to throw at exchangeCode.
        if (!TOKEN_EXCHANGE_IMPLEMENTED || clientId.isBlank()) {
            logger.warn(
                "AppleOAuthProvider is not available — using stub authorization URL. " +
                    "Sign in with Apple token exchange is not yet implemented."
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
        if (!TOKEN_EXCHANGE_IMPLEMENTED || clientId.isBlank()) {
            throw OAuthException(
                "Sign in with Apple is not available — token exchange is not yet implemented. " +
                    "The provider is disabled until implementation is complete."
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
