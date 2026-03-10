package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.AuthTokenResponse
import dev.outerstellar.starter.model.LoginRequest
import dev.outerstellar.starter.security.SecurityService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto

class AuthApi(private val securityService: SecurityService) : ServerRoutes {
    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val tokenResponseLens = Body.auto<AuthTokenResponse>().toLens()

    override val routes: List<ContractRoute> = listOf(
        "/api/v1/auth/login" meta {
            summary = "Login to get API token"
            receiving(loginRequestLens)
            returning(Status.OK, tokenResponseLens to AuthTokenResponse("", "", ""))
            returning(Status.UNAUTHORIZED to "Invalid credentials")
        } bindContract POST to { request ->
            val login = loginRequestLens(request)
            val user = securityService.authenticate(login.username, login.password)
            
            if (user != null) {
                Response(Status.OK).with(tokenResponseLens of AuthTokenResponse(
                    token = user.id.toString(),
                    username = user.username,
                    role = user.role.name
                ))
            } else {
                Response(Status.UNAUTHORIZED).body("Invalid credentials")
            }
        }
    )
}
