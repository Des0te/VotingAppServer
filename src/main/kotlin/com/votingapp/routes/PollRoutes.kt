package com.votingapp.routes

import com.votingapp.api.CreatePollRequest
import com.votingapp.api.UpdatePollRequest
import com.votingapp.api.VoteRequest
import com.votingapp.domain.UserRole
import com.votingapp.service.AppException
import com.votingapp.service.PollService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.pollRoutes(pollService: PollService) {
    route("/polls") {
        get("/active") {
            call.respond(
                pollService.active(
                    page = call.page(),
                    size = call.size(),
                )
            )
        }
        get("/{id}") {
            call.respond(pollService.getById(call.pollId()))
        }
        get("/{id}/results") {
            call.respond(pollService.results(call.pollId()))
        }

        authenticate("auth-jwt") {
            post {
                val response = pollService.create(call.userId(), call.receive<CreatePollRequest>())
                call.respond(HttpStatusCode.Created, response)
            }
            put("/{id}") {
                call.respond(pollService.update(call.userId(), call.pollId(), call.receive<UpdatePollRequest>()))
            }
            delete("/{id}") {
                pollService.delete(call.userId(), call.userRole(), call.pollId())
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/vote") {
                call.respond(pollService.vote(call.userId(), call.pollId(), call.receive<VoteRequest>()))
            }
        }
    }

    get("/search") {
        val query = call.request.queryParameters["q"].orEmpty()
        call.respond(pollService.search(query, call.page(), call.size()))
    }
}

private fun io.ktor.server.application.ApplicationCall.pollId(): UUID {
    val id = parameters["id"] ?: throw AppException("Не указан идентификатор")
    return try {
        UUID.fromString(id)
    } catch (_: Exception) {
        throw AppException("Некорректный идентификатор")
    }
}

private fun io.ktor.server.application.ApplicationCall.page(): Int =
    request.queryParameters["page"]?.toIntOrNull() ?: 0

private fun io.ktor.server.application.ApplicationCall.size(): Int =
    request.queryParameters["size"]?.toIntOrNull() ?: 20

private fun io.ktor.server.application.ApplicationCall.userId(): UUID {
    val principal = principal<JWTPrincipal>() ?: throw AppException("Требуется авторизация", HttpStatusCode.Unauthorized)
    val value = principal.payload.getClaim("userId").asString()
    return try {
        UUID.fromString(value)
    } catch (_: Exception) {
        throw AppException("Некорректный токен", HttpStatusCode.Unauthorized)
    }
}

private fun io.ktor.server.application.ApplicationCall.userRole(): UserRole {
    val principal = principal<JWTPrincipal>() ?: throw AppException("Требуется авторизация", HttpStatusCode.Unauthorized)
    return runCatching {
        UserRole.valueOf(principal.payload.getClaim("role").asString())
    }.getOrDefault(UserRole.USER)
}
