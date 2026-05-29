package com.votingapp.domain

import java.time.Instant
import java.util.UUID

enum class UserRole {
    USER,
    ADMIN,
}

enum class ChoiceType {
    SINGLE,
    MULTIPLE,
}

data class User(
    val id: UUID,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
)

data class Poll(
    val id: UUID,
    val question: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val choiceType: ChoiceType,
    val anonymous: Boolean,
    val authorId: UUID,
    val maxChoices: Int,
    val options: List<PollOption>,
)

data class PollOption(
    val id: UUID,
    val pollId: UUID,
    val text: String,
)

data class ResultOption(
    val optionId: UUID,
    val text: String,
    val votes: Int,
    val percent: Double,
)

data class PollResults(
    val pollId: UUID,
    val totalVoters: Int,
    val options: List<ResultOption>,
)
