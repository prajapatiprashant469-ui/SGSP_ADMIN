package com.SGSP_ADMIN.repository

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

@Document(collection = "admins")
data class Admin(
    @Id val id: String? = null,
    val name: String,
    val email: String,
    val role: String,
    // plain-text password for demo only. In production store hashed password and compare accordingly.
    val password: String? = null,
    val lastLoginAt: String? = null
)

interface AdminRepository : ReactiveMongoRepository<Admin, String> {
    fun findByEmail(email: String): Mono<Admin>
    fun findByEmailAndPassword(email: String, password: String): Mono<Admin>
}
