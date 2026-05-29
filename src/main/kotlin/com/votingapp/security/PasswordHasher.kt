package com.votingapp.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class PasswordHasher {
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = digest(salt, password)
        return "${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = Base64.getDecoder().decode(parts[0])
        val expected = Base64.getDecoder().decode(parts[1])
        return digest(salt, password).contentEquals(expected)
    }

    private fun digest(salt: ByteArray, password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(password.toByteArray())
    }
}
