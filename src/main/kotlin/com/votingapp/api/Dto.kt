package com.votingapp.api

import com.votingapp.domain.ChoiceType
import com.votingapp.domain.Poll
import com.votingapp.domain.PollResults
import com.votingapp.domain.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse,
)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
)

@Serializable
data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    val startsAt: String,
    val endsAt: String,
    val choiceType: ChoiceType = ChoiceType.SINGLE,
    val anonymous: Boolean = false,
    val maxChoices: Int = 1,
)

@Serializable
data class UpdatePollRequest(
    val question: String,
    val options: List<String>,
    val startsAt: String,
    val endsAt: String,
    val choiceType: ChoiceType = ChoiceType.SINGLE,
    val anonymous: Boolean = false,
    val maxChoices: Int = 1,
)

@Serializable
data class VoteRequest(
    val optionIds: List<String>,
)

@Serializable
data class VoteResponse(
    val message: String,
    val results: ResultsResponse? = null,
)

@Serializable
data class PollResponse(
    val id: String,
    val question: String,
    val startsAt: String,
    val endsAt: String,
    val choiceType: String,
    val anonymous: Boolean,
    val authorId: String,
    val maxChoices: Int,
    val options: List<OptionResponse>,
)

@Serializable
data class OptionResponse(
    val id: String,
    val text: String,
)

@Serializable
data class ResultsResponse(
    val pollId: String,
    val totalVoters: Int,
    val options: List<ResultOptionResponse>,
)

@Serializable
data class ResultOptionResponse(
    val optionId: String,
    val text: String,
    val votes: Int,
    val percent: Double,
)

fun User.toResponse() = UserResponse(
    id = id.toString(),
    name = name,
    email = email,
    role = role.name,
)

fun Poll.toResponse() = PollResponse(
    id = id.toString(),
    question = question,
    startsAt = startsAt.toString(),
    endsAt = endsAt.toString(),
    choiceType = choiceType.name,
    anonymous = anonymous,
    authorId = authorId.toString(),
    maxChoices = maxChoices,
    options = options.map { OptionResponse(it.id.toString(), it.text) },
)

fun PollResults.toResponse() = ResultsResponse(
    pollId = pollId.toString(),
    totalVoters = totalVoters,
    options = options.map {
        ResultOptionResponse(
            optionId = it.optionId.toString(),
            text = it.text,
            votes = it.votes,
            percent = it.percent,
        )
    },
)
