package com.SGSP_ADMIN.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class TokenBlacklistService {
    // token -> expiryEpochMillis
    private val blacklist = ConcurrentHashMap<String, Long>()

    fun blacklist(token: String, expiryEpochMillis: Long) {
        if (token.isBlank()) return
        blacklist[token] = expiryEpochMillis
    }

    fun isBlacklisted(token: String): Boolean {
        val exp = blacklist[token] ?: return false
        val now = System.currentTimeMillis()
        return if (now < exp) {
            true
        } else {
            // cleanup expired record
            blacklist.remove(token)
            false
        }
    }
}
