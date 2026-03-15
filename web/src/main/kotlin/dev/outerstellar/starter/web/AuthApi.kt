package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.ApiKeySummary
import dev.outerstellar.starter.model.AuthTokenResponse
import dev.outerstellar.starter.model.ChangePasswordRequest
import dev.outerstellar.starter.model.CreateApiKeyRequest
import dev.outerstellar.starter.model.CreateApiKeyResponse
import dev.outerstellar.starter.model.InsufficientPermissionException
import dev.outerstellar.starter.model.LoginRequest
import dev.outerstellar.starter.model.PasswordResetConfirm
import dev.outerstellar.starter.model.PasswordResetRequest
import dev.outerstellar.starter.model.RegisterRequest
import dev.outerstellar.starter.model.UpdateNotificationPrefsRequest
import dev.outerstellar.starter.model.UpdateProfileRequest
import dev.outerstellar.starter.model.UserProfileResponse
import dev.outerstellar.starter.model.UsernameAlreadyExistsException
import dev.outerstellar.starter.model.WeakPasswordException
import dev.outerstellar.starter.security.SecurityRules
import dev.outerstellar.starter.security.SecurityService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.long

class AuthApi(private val securityService: SecurityService) : ServerRoutes {
    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val registerRequestLens = Body.auto<RegisterRequest>().toLens()
    private val tokenResponseLens = Body.auto<AuthTokenResponse>().toLens()
    private val changePasswordLens = Body.auto<ChangePasswordRequest>().toLens()
    private val resetRequestLens = Body.auto<PasswordResetRequest>().toLens()
    private val resetConfirmLens = Body.auto<PasswordResetConfirm>().toLens()
    private val createApiKeyLens = Body.auto<CreateApiKeyRequest>().toLens()
    private val createApiKeyResponseLens = Body.auto<CreateApiKeyResponse>().toLens()
    private val apiKeySummaryListLens = Body.auto<List<ApiKeySummary>>().toLens()
    private val apiKeyIdPath = Path.long().of("id")
    private val updateProfileLens = Body.auto<UpdateProfileRequest>().toLens()
    private val userProfileResponseLens = Body.auto<UserProfileResponse>().toLens()
    private val updateNotifPrefsLens = Body.auto<UpdateNotificationPrefsRequest>().toLens()

    /** Routes that require bearer authentication (password change, API keys). */
    val bearerRoutes: List<ContractRoute> =
        listOf(
            "/api/v1/auth/password" meta
                {
                    summary = "Change password (bearer-auth protected)"
                    receiving(changePasswordLens)
                    returning(Status.OK to "Password changed")
                    returning(Status.BAD_REQUEST to "Invalid password")
                } bindContract
                PUT to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    try {
                        val body = changePasswordLens(request)
                        securityService.changePassword(
                            user.id,
                            body.currentPassword,
                            body.newPassword,
                        )
                        Response(Status.OK).body("Password changed successfully")
                    } catch (e: WeakPasswordException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid password")
                    }
                },
            "/api/v1/auth/api-keys" meta
                {
                    summary = "Create a new API key"
                    receiving(createApiKeyLens)
                    returning(Status.OK to "API key created")
                    returning(Status.BAD_REQUEST to "Invalid request")
                } bindContract
                POST to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    try {
                        val body = createApiKeyLens(request)
                        val response = securityService.createApiKey(user.id, body.name)
                        Response(Status.OK).with(createApiKeyResponseLens of response)
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid request")
                    }
                },
            "/api/v1/auth/api-keys" meta
                {
                    summary = "List user's API keys"
                    returning(Status.OK to "API keys list")
                } bindContract
                GET to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    val keys = securityService.listApiKeys(user.id)
                    Response(Status.OK).with(apiKeySummaryListLens of keys)
                },
            "/api/v1/auth/api-keys" / apiKeyIdPath meta
                {
                    summary = "Delete an API key"
                    returning(Status.OK to "API key deleted")
                } bindContract
                DELETE to
                { id ->
                    { request ->
                        val user = SecurityRules.USER_KEY(request)!!
                        securityService.deleteApiKey(user.id, id)
                        Response(Status.OK).body("API key deleted")
                    }
                },
            "/api/v1/auth/profile" meta
                {
                    summary = "Get current user profile"
                    returning(
                        Status.OK,
                        userProfileResponseLens to UserProfileResponse("", "", null, true, true),
                    )
                } bindContract
                GET to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    Response(Status.OK)
                        .with(
                            userProfileResponseLens of
                                UserProfileResponse(
                                    username = user.username,
                                    email = user.email,
                                    avatarUrl = user.avatarUrl,
                                    emailNotificationsEnabled = user.emailNotificationsEnabled,
                                    pushNotificationsEnabled = user.pushNotificationsEnabled,
                                )
                        )
                },
            "/api/v1/auth/profile" meta
                {
                    summary = "Update current user profile"
                    receiving(updateProfileLens)
                    returning(Status.OK to "Profile updated")
                    returning(Status.CONFLICT to "Username already taken")
                    returning(Status.BAD_REQUEST to "Invalid request")
                } bindContract
                PUT to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    try {
                        val body = updateProfileLens(request)
                        securityService.updateProfile(
                            user.id,
                            body.email,
                            body.username,
                            body.avatarUrl,
                        )
                        Response(Status.OK).body("Profile updated")
                    } catch (e: UsernameAlreadyExistsException) {
                        Response(Status.CONFLICT).body(e.message ?: "Username already taken")
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid request")
                    }
                },
            "/api/v1/auth/notification-preferences" meta
                {
                    summary = "Update notification preferences"
                    receiving(updateNotifPrefsLens)
                    returning(Status.OK to "Preferences updated")
                } bindContract
                PUT to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    val body = updateNotifPrefsLens(request)
                    securityService.updateNotificationPreferences(
                        user.id,
                        body.emailEnabled,
                        body.pushEnabled,
                    )
                    Response(Status.OK).body("Preferences updated")
                },
            "/api/v1/auth/account" meta
                {
                    summary = "Delete own account"
                    returning(Status.OK to "Account deleted")
                    returning(Status.FORBIDDEN to "Cannot delete the only admin")
                } bindContract
                DELETE to
                { request ->
                    val user = SecurityRules.USER_KEY(request)!!
                    try {
                        securityService.deleteAccount(user.id)
                        Response(Status.OK).body("Account deleted")
                    } catch (e: InsufficientPermissionException) {
                        Response(Status.FORBIDDEN).body(e.message ?: "Cannot delete the only admin")
                    }
                },
        )

    /** Public routes (login, register) - no auth required. */
    override val routes: List<ContractRoute> =
        listOf(
            "/api/v1/auth/login" meta
                {
                    summary = "Login to get API token"
                    receiving(loginRequestLens)
                    returning(Status.OK, tokenResponseLens to AuthTokenResponse("", "", ""))
                    returning(Status.UNAUTHORIZED to "Invalid credentials")
                } bindContract
                POST to
                { request ->
                    val login = loginRequestLens(request)
                    val user = securityService.authenticate(login.username, login.password)

                    if (user != null) {
                        Response(Status.OK)
                            .with(
                                tokenResponseLens of
                                    AuthTokenResponse(
                                        token = user.id.toString(),
                                        username = user.username,
                                        role = user.role.name,
                                    )
                            )
                    } else {
                        Response(Status.UNAUTHORIZED).body("Invalid credentials")
                    }
                },
            "/api/v1/auth/register" meta
                {
                    summary = "Register user and return API token"
                    receiving(registerRequestLens)
                    returning(Status.OK, tokenResponseLens to AuthTokenResponse("", "", ""))
                    returning(Status.BAD_REQUEST to "Invalid registration request")
                    returning(Status.CONFLICT to "Username already exists")
                } bindContract
                POST to
                { request ->
                    val register = registerRequestLens(request)
                    try {
                        val user = securityService.register(register.username, register.password)
                        Response(Status.OK)
                            .with(
                                tokenResponseLens of
                                    AuthTokenResponse(
                                        token = user.id.toString(),
                                        username = user.username,
                                        role = user.role.name,
                                    )
                            )
                    } catch (e: UsernameAlreadyExistsException) {
                        Response(Status.CONFLICT).body(e.message ?: "Username already taken")
                    } catch (e: WeakPasswordException) {
                        Response(Status.BAD_REQUEST)
                            .body(e.message ?: "Invalid registration request")
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST)
                            .body(e.message ?: "Invalid registration request")
                    }
                },
            "/api/v1/auth/reset-request" meta
                {
                    summary = "Request a password reset"
                    receiving(resetRequestLens)
                    returning(Status.OK to "Reset request accepted")
                } bindContract
                POST to
                { request ->
                    val body = resetRequestLens(request)
                    securityService.requestPasswordReset(body.email)
                    Response(Status.OK).body("Reset request accepted")
                },
            "/api/v1/auth/reset-confirm" meta
                {
                    summary = "Confirm password reset with token"
                    receiving(resetConfirmLens)
                    returning(Status.OK to "Password has been reset")
                    returning(Status.BAD_REQUEST to "Invalid or expired token")
                } bindContract
                POST to
                { request ->
                    val body = resetConfirmLens(request)
                    try {
                        securityService.resetPassword(body.token, body.newPassword)
                        Response(Status.OK).body("Password has been reset")
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid or expired token")
                    } catch (e: WeakPasswordException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid password")
                    }
                },
        )
}
