package com.votingapp.data

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val jwt: JwtConfig,
    val database: DatabaseConfig,
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            return AppConfig(
                jwt = JwtConfig(
                    secret = config.value("ktor.jwt.secret", "dev-secret-change-before-production"),
                    issuer = config.value("ktor.jwt.issuer", "voting-server"),
                    audience = config.value("ktor.jwt.audience", "voting-client"),
                    realm = config.value("ktor.jwt.realm", "VotingApp"),
                ),
                database = DatabaseConfig(
                    url = config.value("ktor.database.url", "jdbc:postgresql://localhost:5432/voting_app"),
                    user = config.value("ktor.database.user", "postgres"),
                    password = config.value("ktor.database.password", "postgres"),
                )
            )
        }

        private fun ApplicationConfig.value(path: String, default: String): String =
            propertyOrNull(path)?.getString() ?: default
    }
}

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
)

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)
