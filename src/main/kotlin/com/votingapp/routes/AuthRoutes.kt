package com.votingapp.routes

import com.votingapp.api.LoginRequest
import com.votingapp.api.RegisterRequest
import com.votingapp.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/register") {
            val response = authService.register(call.receive<RegisterRequest>())
            call.respond(HttpStatusCode.Created, response)
        }
        post("/login") {
            call.respond(authService.login(call.receive<LoginRequest>()))
        }
    }
}
