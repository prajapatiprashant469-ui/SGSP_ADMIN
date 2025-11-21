package com.SGSP_ADMIN.repository

import reactor.core.publisher.Mono

data class Admin(val id: String, val name: String, val email: String, val role: String)

// Simple demo repository — replace with real reactive DB and hashed passwords in production.
class AdminRepository {
    // NOTE: plain-text password only for demo purposes.
    private val store = listOf(
        mapOf(
            "id" to "a1",
            "name" to "Super Admin",
            "email" to "admin@example.com",
            "password" to "Admin@123",
            "role" to "SUPER_ADMIN"
        )
    )

    fun findByEmailAndPassword(email: String, password: String): Mono<Admin> {
        val entry = store.firstOrNull { it["email"] == email && it["password"] == password }
        return if (entry != null) {
            Mono.just(Admin(entry["id"] as String, entry["name"] as String, entry["email"] as String, entry["role"] as String))
        } else {
            Mono.empty()
        }
    }
}
