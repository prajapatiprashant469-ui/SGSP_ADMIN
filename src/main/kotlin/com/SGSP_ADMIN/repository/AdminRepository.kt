package com.SGSP_ADMIN.repository

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

@Document(collection = "admins")
data class Admin(
    @Id var id: String? = null,
    var name: String,
    var email: String,
    var role: String,
    // plain-text password for demo only. In production store hashed password and compare accordingly.
    var password: String? = null,
    var lastLoginAt: String? = null,
    // new fields
    var active: Boolean = true,
    var createdAt: String? = null,
    var updatedAt: String? = null
)

interface AdminRepository : ReactiveMongoRepository<Admin, String> {
    fun findByEmail(email: String): Mono<Admin>
    fun findByEmailAndPassword(email: String, password: String): Mono<Admin>
}
