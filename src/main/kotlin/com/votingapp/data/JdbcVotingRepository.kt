package com.votingapp.data

import com.votingapp.domain.ChoiceType
import com.votingapp.domain.Poll
import com.votingapp.domain.PollOption
import com.votingapp.domain.PollResults
import com.votingapp.domain.ResultOption
import com.votingapp.domain.User
import com.votingapp.domain.UserRole
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class JdbcVotingRepository(private val config: DatabaseConfig) : VotingRepository {
    init {
        Class.forName("org.postgresql.Driver")
    }

    override fun init() {
        connection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    create table if not exists users (
                        id uuid primary key,
                        name text not null,
                        email text not null unique,
                        password_hash text not null,
                        role text not null
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    create table if not exists polls (
                        id uuid primary key,
                        question text not null,
                        starts_at timestamptz not null,
                        ends_at timestamptz not null,
                        choice_type text not null,
                        anonymous boolean not null,
                        author_id uuid not null references users(id) on delete cascade,
                        max_choices int not null
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    create table if not exists poll_options (
                        id uuid primary key,
                        poll_id uuid not null references polls(id) on delete cascade,
                        text text not null
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    create table if not exists votes (
                        id uuid primary key,
                        user_id uuid not null references users(id) on delete cascade,
                        poll_id uuid not null references polls(id) on delete cascade,
                        option_id uuid not null references poll_options(id) on delete cascade,
                        created_at timestamptz not null,
                        unique(user_id, poll_id, option_id)
                    )
                    """.trimIndent()
                )
                st.executeUpdate("create index if not exists polls_question_idx on polls using gin (to_tsvector('simple', question))")
                st.executeUpdate("create index if not exists polls_active_idx on polls (starts_at, ends_at)")
            }
        }
    }

    override fun countUsers(): Int = connection().use { conn ->
        conn.prepareStatement("select count(*) from users").use { ps ->
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    override fun createUser(user: User) {
        connection().use { conn ->
            conn.prepareStatement(
                "insert into users(id, name, email, password_hash, role) values (?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setUuid(1, user.id)
                ps.setString(2, user.name)
                ps.setString(3, user.email)
                ps.setString(4, user.passwordHash)
                ps.setString(5, user.role.name)
                ps.executeUpdate()
            }
        }
    }

    override fun findUserByEmail(email: String): User? = connection().use { conn ->
        conn.prepareStatement("select * from users where email = ?").use { ps ->
            ps.setString(1, email)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    override fun findUserById(id: UUID): User? = connection().use { conn ->
        conn.prepareStatement("select * from users where id = ?").use { ps ->
            ps.setUuid(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }
    }

    override fun createPoll(poll: Poll): Poll {
        transaction { conn ->
            insertPoll(conn, poll)
            insertOptions(conn, poll)
        }
        return poll
    }

    override fun updatePoll(poll: Poll): Poll {
        transaction { conn ->
            conn.prepareStatement(
                """
                update polls
                set question = ?, starts_at = ?, ends_at = ?, choice_type = ?, anonymous = ?, max_choices = ?
                where id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, poll.question)
                ps.setInstant(2, poll.startsAt)
                ps.setInstant(3, poll.endsAt)
                ps.setString(4, poll.choiceType.name)
                ps.setBoolean(5, poll.anonymous)
                ps.setInt(6, poll.maxChoices)
                ps.setUuid(7, poll.id)
                ps.executeUpdate()
            }
            conn.prepareStatement("delete from poll_options where poll_id = ?").use { ps ->
                ps.setUuid(1, poll.id)
                ps.executeUpdate()
            }
            insertOptions(conn, poll)
        }
        return poll
    }

    override fun deletePoll(id: UUID) {
        connection().use { conn ->
            conn.prepareStatement("delete from polls where id = ?").use { ps ->
                ps.setUuid(1, id)
                ps.executeUpdate()
            }
        }
    }

    override fun findPollById(id: UUID): Poll? = connection().use { conn ->
        conn.prepareStatement("select * from polls where id = ?").use { ps ->
            ps.setUuid(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPoll(conn) else null }
        }
    }

    override fun activePolls(page: Int, size: Int): List<Poll> = connection().use { conn ->
        conn.prepareStatement(
            """
            select * from polls
            where starts_at <= now() and ends_at >= now()
            order by starts_at desc
            limit ? offset ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, size)
            ps.setInt(2, page * size)
            ps.executeQuery().use { rs -> rs.polls(conn) }
        }
    }

    override fun searchPolls(query: String, page: Int, size: Int): List<Poll> = connection().use { conn ->
        conn.prepareStatement(
            """
            select * from polls
            where lower(question) like ?
            order by starts_at desc
            limit ? offset ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, "%${query.lowercase()}%")
            ps.setInt(2, size)
            ps.setInt(3, page * size)
            ps.executeQuery().use { rs -> rs.polls(conn) }
        }
    }

    override fun hasVotes(userId: UUID, pollId: UUID): Boolean = connection().use { conn ->
        conn.prepareStatement("select count(*) from votes where user_id = ? and poll_id = ?").use { ps ->
            ps.setUuid(1, userId)
            ps.setUuid(2, pollId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1) > 0
            }
        }
    }

    override fun saveVotes(userId: UUID, pollId: UUID, optionIds: List<UUID>) {
        transaction { conn ->
            conn.prepareStatement(
                "insert into votes(id, user_id, poll_id, option_id, created_at) values (?, ?, ?, ?, ?)"
            ).use { ps ->
                optionIds.forEach { optionId ->
                    ps.setUuid(1, UUID.randomUUID())
                    ps.setUuid(2, userId)
                    ps.setUuid(3, pollId)
                    ps.setUuid(4, optionId)
                    ps.setInstant(5, Instant.now())
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    override fun results(pollId: UUID): PollResults? = connection().use { conn ->
        val poll = findPollById(pollId) ?: return null
        val rows = conn.prepareStatement(
            """
            select o.id, o.text, count(v.id) as votes_count
            from poll_options o
            left join votes v on v.option_id = o.id
            where o.poll_id = ?
            group by o.id, o.text
            order by o.text
            """.trimIndent()
        ).use { ps ->
            ps.setUuid(1, pollId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<Pair<PollOption, Int>>()
                while (rs.next()) {
                    list += PollOption(rs.getUuid("id"), pollId, rs.getString("text")) to rs.getInt("votes_count")
                }
                list
            }
        }
        val totalVoters = conn.prepareStatement("select count(distinct user_id) from votes where poll_id = ?").use { ps ->
            ps.setUuid(1, pollId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
        val totalVotes = rows.sumOf { it.second }.coerceAtLeast(1)
        PollResults(
            pollId = poll.id,
            totalVoters = totalVoters,
            options = rows.map { (option, votes) ->
                ResultOption(
                    optionId = option.id,
                    text = option.text,
                    votes = votes,
                    percent = votes * 100.0 / totalVotes,
                )
            },
        )
    }

    private fun insertPoll(conn: Connection, poll: Poll) {
        conn.prepareStatement(
            """
            insert into polls(id, question, starts_at, ends_at, choice_type, anonymous, author_id, max_choices)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setUuid(1, poll.id)
            ps.setString(2, poll.question)
            ps.setInstant(3, poll.startsAt)
            ps.setInstant(4, poll.endsAt)
            ps.setString(5, poll.choiceType.name)
            ps.setBoolean(6, poll.anonymous)
            ps.setUuid(7, poll.authorId)
            ps.setInt(8, poll.maxChoices)
            ps.executeUpdate()
        }
    }

    private fun insertOptions(conn: Connection, poll: Poll) {
        conn.prepareStatement(
            "insert into poll_options(id, poll_id, text) values (?, ?, ?)"
        ).use { ps ->
            poll.options.forEach { option ->
                ps.setUuid(1, option.id)
                ps.setUuid(2, poll.id)
                ps.setString(3, option.text)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun loadOptions(conn: Connection, pollId: UUID): List<PollOption> {
        return conn.prepareStatement("select * from poll_options where poll_id = ? order by text").use { ps ->
            ps.setUuid(1, pollId)
            ps.executeQuery().use { rs ->
                val options = mutableListOf<PollOption>()
                while (rs.next()) {
                    options += PollOption(
                        id = rs.getUuid("id"),
                        pollId = pollId,
                        text = rs.getString("text"),
                    )
                }
                options
            }
        }
    }

    private fun transaction(block: (Connection) -> Unit) {
        connection().use { conn ->
            conn.autoCommit = false
            try {
                block(conn)
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(config.url, config.user, config.password)

    private fun ResultSet.polls(conn: Connection): List<Poll> {
        val list = mutableListOf<Poll>()
        while (next()) {
            list += toPoll(conn)
        }
        return list
    }

    private fun ResultSet.toUser(): User = User(
        id = getUuid("id"),
        name = getString("name"),
        email = getString("email"),
        passwordHash = getString("password_hash"),
        role = UserRole.valueOf(getString("role")),
    )

    private fun ResultSet.toPoll(conn: Connection): Poll {
        val id = getUuid("id")
        return Poll(
            id = id,
            question = getString("question"),
            startsAt = getInstant("starts_at"),
            endsAt = getInstant("ends_at"),
            choiceType = ChoiceType.valueOf(getString("choice_type")),
            anonymous = getBoolean("anonymous"),
            authorId = getUuid("author_id"),
            maxChoices = getInt("max_choices"),
            options = loadOptions(conn, id),
        )
    }

    private fun ResultSet.getUuid(name: String): UUID = getObject(name, UUID::class.java)

    private fun ResultSet.getInstant(name: String): Instant = getTimestamp(name).toInstant()

    private fun java.sql.PreparedStatement.setUuid(index: Int, value: UUID) {
        setObject(index, value)
    }

    private fun java.sql.PreparedStatement.setInstant(index: Int, value: Instant) {
        setTimestamp(index, Timestamp.from(value))
    }
}
