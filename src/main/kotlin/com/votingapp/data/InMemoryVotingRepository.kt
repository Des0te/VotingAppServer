package com.votingapp.data

import com.votingapp.domain.Poll
import com.votingapp.domain.PollResults
import com.votingapp.domain.ResultOption
import com.votingapp.domain.User
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryVotingRepository : VotingRepository {
    private val users = ConcurrentHashMap<UUID, User>()
    private val polls = ConcurrentHashMap<UUID, Poll>()
    private val votes = mutableListOf<VoteRow>()

    override fun init() = Unit

    override fun countUsers(): Int = users.size

    override fun createUser(user: User) {
        users[user.id] = user
    }

    override fun findUserByEmail(email: String): User? =
        users.values.firstOrNull { it.email == email }

    override fun findUserById(id: UUID): User? = users[id]

    override fun createPoll(poll: Poll): Poll {
        polls[poll.id] = poll
        return poll
    }

    override fun updatePoll(poll: Poll): Poll {
        polls[poll.id] = poll
        synchronized(votes) {
            votes.removeAll { it.pollId == poll.id && poll.options.none { option -> option.id == it.optionId } }
        }
        return poll
    }

    override fun deletePoll(id: UUID) {
        polls.remove(id)
        synchronized(votes) {
            votes.removeAll { it.pollId == id }
        }
    }

    override fun findPollById(id: UUID): Poll? = polls[id]

    override fun activePolls(page: Int, size: Int): List<Poll> {
        val now = Instant.now()
        return polls.values
            .filter { !now.isBefore(it.startsAt) && !now.isAfter(it.endsAt) }
            .sortedByDescending { it.startsAt }
            .drop(page * size)
            .take(size)
    }

    override fun searchPolls(query: String, page: Int, size: Int): List<Poll> {
        return polls.values
            .filter { it.question.contains(query, ignoreCase = true) }
            .sortedByDescending { it.startsAt }
            .drop(page * size)
            .take(size)
    }

    override fun hasVotes(userId: UUID, pollId: UUID): Boolean =
        synchronized(votes) { votes.any { it.userId == userId && it.pollId == pollId } }

    override fun saveVotes(userId: UUID, pollId: UUID, optionIds: List<UUID>) {
        synchronized(votes) {
            optionIds.forEach { optionId -> votes += VoteRow(userId, pollId, optionId) }
        }
    }

    override fun results(pollId: UUID): PollResults? {
        val poll = polls[pollId] ?: return null
        val rows = synchronized(votes) { votes.filter { it.pollId == pollId } }
        val totalVotes = rows.size.coerceAtLeast(1)
        val totalVoters = rows.map { it.userId }.toSet().size
        return PollResults(
            pollId = pollId,
            totalVoters = totalVoters,
            options = poll.options.map { option ->
                val count = rows.count { it.optionId == option.id }
                ResultOption(
                    optionId = option.id,
                    text = option.text,
                    votes = count,
                    percent = count * 100.0 / totalVotes,
                )
            },
        )
    }

    private data class VoteRow(
        val userId: UUID,
        val pollId: UUID,
        val optionId: UUID,
    )
}
