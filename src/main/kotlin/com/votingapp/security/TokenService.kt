package com.votingapp.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.votingapp.data.JwtConfig
import com.votingapp.domain.User
import java.time.Instant
import java.util.Date

class TokenService(private val config: JwtConfig) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        .build()

    fun create(user: User): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("userId", user.id.toString())
            .withClaim("role", user.role.name)
            .withExpiresAt(Date.from(now.plusSeconds(60 * 60 * 24)))
            .sign(algorithm)
    }
}
