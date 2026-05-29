package com.votingapp.data

import com.votingapp.domain.Poll
import com.votingapp.domain.PollResults
import com.votingapp.domain.User
import java.util.UUID

interface VotingRepository {
    fun init()
    fun countUsers(): Int
    fun createUser(user: User)
    fun findUserByEmail(email: String): User?
    fun findUserById(id: UUID): User?
    fun createPoll(poll: Poll): Poll
    fun updatePoll(poll: Poll): Poll
    fun deletePoll(id: UUID)
    fun findPollById(id: UUID): Poll?
    fun activePolls(page: Int, size: Int): List<Poll>
    fun searchPolls(query: String, page: Int, size: Int): List<Poll>
    fun hasVotes(userId: UUID, pollId: UUID): Boolean
    fun saveVotes(userId: UUID, pollId: UUID, optionIds: List<UUID>)
    fun results(pollId: UUID): PollResults?
}
