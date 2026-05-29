package com.votingapp

import com.votingapp.data.AppConfig
import com.votingapp.data.JdbcVotingRepository
import com.votingapp.data.VotingRepository
import com.votingapp.routes.authRoutes
import com.votingapp.routes.pollRoutes
import com.votingapp.security.TokenService
import com.votingapp.service.AppException
import com.votingapp.service.AuthService
import com.votingapp.service.PollService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun Application.module(repository: VotingRepository? = null) {
    val appLog = environment.log
    val config = AppConfig.from(environment.config)
    val repo = repository ?: JdbcVotingRepository(config.database)
    repo.init()

    val tokenService = TokenService(config.jwt)
    val authService = AuthService(repo, tokenService)
    val pollService = PollService(repo)

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                isLenient = true
            }
        )
    }

    install(CallLogging) {
        level = Level.INFO
        format { call -> "${call.request.httpMethod.value} ${call.request.path()}" }
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
    }

    install(StatusPages) {
        exception<AppException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<Throwable> { call, cause ->
            appLog.error("Ошибка сервера", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Внутренняя ошибка сервера"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.jwt.realm
            verifier(tokenService.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asString().isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
        }
    }

    routing {
        get("/") {
            call.respond(mapOf("status" to "ok", "name" to "VotingApp"))
        }
        authRoutes(authService)
        pollRoutes(pollService)
    }
}

@Serializable
data class ErrorResponse(val message: String)
