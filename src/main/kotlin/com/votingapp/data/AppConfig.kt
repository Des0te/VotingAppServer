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
                    secret = env("JWT_SECRET") ?: config.value("ktor.jwt.secret", "dev-secret-change-before-production"),
                    issuer = config.value("ktor.jwt.issuer", "voting-server"),
                    audience = config.value("ktor.jwt.audience", "voting-client"),
                    realm = config.value("ktor.jwt.realm", "VotingApp"),
                ),
                database = DatabaseConfig(
                    mode = env("DB_MODE") ?: config.value("ktor.database.mode", "auto"),
                    url = env("DATABASE_URL") ?: config.value("ktor.database.url", "jdbc:postgresql://localhost:5432/voting_app"),
                    user = env("DB_USER") ?: config.value("ktor.database.user", "postgres"),
                    password = env("DB_PASSWORD") ?: config.value("ktor.database.password", "postgres"),
                )
            )
        }

        private fun ApplicationConfig.value(path: String, default: String): String =
            propertyOrNull(path)?.getString() ?: default

        private fun env(name: String): String? =
            System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
)

data class DatabaseConfig(
    val mode: String,
    val url: String,
    val user: String,
    val password: String,
) {
    val memoryMode: Boolean
        get() = mode.equals("memory", ignoreCase = true)

    val autoMode: Boolean
        get() = mode.equals("auto", ignoreCase = true)
}
