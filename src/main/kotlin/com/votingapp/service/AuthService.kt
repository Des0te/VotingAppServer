package com.votingapp.service

import com.votingapp.api.AuthResponse
import com.votingapp.api.LoginRequest
import com.votingapp.api.RegisterRequest
import com.votingapp.api.toResponse
import com.votingapp.data.VotingRepository
import com.votingapp.domain.User
import com.votingapp.domain.UserRole
import com.votingapp.security.PasswordHasher
import com.votingapp.security.TokenService
import io.ktor.http.HttpStatusCode
import java.util.UUID

class AuthService(
    private val repository: VotingRepository,
    private val tokenService: TokenService,
) {
    private val hasher = PasswordHasher()

    fun register(request: RegisterRequest): AuthResponse {
        val name = request.name.trim()
        val email = request.email.trim().lowercase()
        val password = request.password

        if (name.length < 2) throw AppException("Имя должно быть не короче 2 символов")
        if (!email.contains("@")) throw AppException("Некорректный email")
        if (password.length < 6) throw AppException("Пароль должен быть не короче 6 символов")
        if (repository.findUserByEmail(email) != null) throw AppException("Пользователь с таким email уже есть")

        val role = if (repository.countUsers() == 0) UserRole.ADMIN else UserRole.USER
        val user = User(
            id = UUID.randomUUID(),
            name = name,
            email = email,
            passwordHash = hasher.hash(password),
            role = role,
        )
        repository.createUser(user)
        return AuthResponse(tokenService.create(user), user.toResponse())
    }

    fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val user = repository.findUserByEmail(email)
            ?: throw AppException("Неверный email или пароль", HttpStatusCode.Unauthorized)
        if (!hasher.verify(request.password, user.passwordHash)) {
            throw AppException("Неверный email или пароль", HttpStatusCode.Unauthorized)
        }
        return AuthResponse(tokenService.create(user), user.toResponse())
    }
}

class AppException(
    override val message: String,
    val status: HttpStatusCode = HttpStatusCode.BadRequest,
) : RuntimeException(message)
