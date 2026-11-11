package com.cmdalton.rocket.bank

import java.security.MessageDigest
import java.security.SecureRandom

/** The grown-up PIN. Matt picks it on first use; only a salted hash is saved, never the PIN. */
object Pin {
    const val LENGTH = 4

    fun isValidFormat(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.toHex()
    }

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    fun matches(pin: String, salt: String?, hash: String?): Boolean {
        if (salt == null || hash == null || !isValidFormat(pin)) return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
