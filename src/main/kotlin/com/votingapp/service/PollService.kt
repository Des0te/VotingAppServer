package com.votingapp.service

import com.votingapp.api.CreatePollRequest
import com.votingapp.api.ResultsResponse
import com.votingapp.api.UpdatePollRequest
import com.votingapp.api.VoteRequest
import com.votingapp.api.VoteResponse
import com.votingapp.api.toResponse
import com.votingapp.data.VotingRepository
import com.votingapp.domain.ChoiceType
import com.votingapp.domain.Poll
import com.votingapp.domain.PollOption
import com.votingapp.domain.UserRole
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.util.UUID

class PollService(private val repository: VotingRepository) {
    fun create(userId: UUID, request: CreatePollRequest) =
        repository.createPoll(buildPoll(UUID.randomUUID(), userId, request)).toResponse()

    fun update(userId: UUID, pollId: UUID, request: UpdatePollRequest) = withPoll(pollId) { old ->
        if (old.authorId != userId) throw AppException("Редактировать голосование может только автор", HttpStatusCode.Forbidden)
        if (!Instant.now().isBefore(old.startsAt)) throw AppException("Голосование уже началось")

        val updated = buildPoll(pollId, userId, request).copy(
            options = request.options.mapIndexed { index, text ->
                PollOption(
                    id = old.options.getOrNull(index)?.id ?: UUID.randomUUID(),
                    pollId = pollId,
                    text = text.trim(),
                )
            }
        )
        repository.updatePoll(updated).toResponse()
    }

    fun delete(userId: UUID, role: UserRole, pollId: UUID) = withPoll(pollId) { poll ->
        if (poll.authorId != userId && role != UserRole.ADMIN) {
            throw AppException("Недостаточно прав для удаления", HttpStatusCode.Forbidden)
        }
        repository.deletePoll(pollId)
    }

    fun active(page: Int, size: Int) = repository.activePolls(cleanPage(page), cleanSize(size)).map { it.toResponse() }

    fun search(query: String, page: Int, size: Int): List<com.votingapp.api.PollResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return repository.searchPolls(q, cleanPage(page), cleanSize(size)).map { it.toResponse() }
    }

    fun getById(id: UUID) = repository.findPollById(id)?.toResponse()
        ?: throw AppException("Голосование не найдено", HttpStatusCode.NotFound)

    fun vote(userId: UUID, pollId: UUID, request: VoteRequest): VoteResponse = withPoll(pollId) { poll ->
        val now = Instant.now()
        if (now.isBefore(poll.startsAt) || now.isAfter(poll.endsAt)) throw AppException("Голосование не активно")
        if (repository.hasVotes(userId, pollId)) throw AppException("Вы уже голосовали")

        val optionIds = request.optionIds.map { parseUuid(it) }.distinct()
        if (optionIds.isEmpty()) throw AppException("Выберите вариант ответа")
        if (poll.choiceType == ChoiceType.SINGLE && optionIds.size != 1) throw AppException("Можно выбрать только один вариант")
        if (poll.choiceType == ChoiceType.MULTIPLE && optionIds.size > poll.maxChoices) {
            throw AppException("Можно выбрать не больше ${poll.maxChoices} вариантов")
        }

        val allowed = poll.options.map { it.id }.toSet()
        if (!allowed.containsAll(optionIds)) throw AppException("Вариант ответа не найден")

        repository.saveVotes(userId, pollId, optionIds)
        val results = if (poll.anonymous) null else repository.results(pollId)?.toResponse()
        VoteResponse("Ваш голос учтён", results)
    }

    fun results(id: UUID): ResultsResponse {
        return repository.results(id)?.toResponse()
            ?: throw AppException("Голосование не найдено", HttpStatusCode.NotFound)
    }

    private fun buildPoll(id: UUID, userId: UUID, request: CreatePollRequest): Poll {
        return buildPoll(id, userId, request.question, request.options, request.startsAt, request.endsAt, request.choiceType, request.anonymous, request.maxChoices)
    }

    private fun buildPoll(id: UUID, userId: UUID, request: UpdatePollRequest): Poll {
        return buildPoll(id, userId, request.question, request.options, request.startsAt, request.endsAt, request.choiceType, request.anonymous, request.maxChoices)
    }

    private fun buildPoll(
        id: UUID,
        userId: UUID,
        question: String,
        options: List<String>,
        startsAtText: String,
        endsAtText: String,
        choiceType: ChoiceType,
        anonymous: Boolean,
        maxChoices: Int,
    ): Poll {
        val cleanQuestion = question.trim()
        val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanQuestion.length < 5) throw AppException("Вопрос должен быть не короче 5 символов")
        if (cleanOptions.size !in 2..10) throw AppException("Нужно указать от 2 до 10 вариантов ответа")

        val startsAt = parseInstant(startsAtText)
        val endsAt = parseInstant(endsAtText)
        if (!endsAt.isAfter(startsAt)) throw AppException("Дата окончания должна быть позже даты начала")

        val fixedMax = if (choiceType == ChoiceType.SINGLE) 1 else maxChoices.coerceIn(1, 5)
        return Poll(
            id = id,
            question = cleanQuestion,
            startsAt = startsAt,
            endsAt = endsAt,
            choiceType = choiceType,
            anonymous = anonymous,
            authorId = userId,
            maxChoices = fixedMax,
            options = cleanOptions.map { PollOption(UUID.randomUUID(), id, it) },
        )
    }

    private fun <T> withPoll(id: UUID, block: (Poll) -> T): T {
        val poll = repository.findPollById(id)
            ?: throw AppException("Голосование не найдено", HttpStatusCode.NotFound)
        return block(poll)
    }

    private fun parseInstant(text: String): Instant = try {
        Instant.parse(text)
    } catch (_: Exception) {
        throw AppException("Дата должна быть в формате ISO-8601")
    }

    private fun parseUuid(text: String): UUID = try {
        UUID.fromString(text)
    } catch (_: Exception) {
        throw AppException("Некорректный идентификатор")
    }

    private fun cleanPage(page: Int) = page.coerceAtLeast(0)

    private fun cleanSize(size: Int) = size.coerceIn(1, 50)
}
