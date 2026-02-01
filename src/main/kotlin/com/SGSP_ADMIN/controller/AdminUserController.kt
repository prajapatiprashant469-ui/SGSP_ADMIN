package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.Admin
import com.SGSP_ADMIN.repository.AdminRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.time.Instant

data class CreateAdminRequest(val name: String, val email: String, val password: String, val role: String? = "ADMIN")
data class UpdateAdminRequest(val name: String? = null, val role: String? = null, val active: Boolean? = null)
data class ResetPasswordRequest(val password: String)

@RestController
@RequestMapping("/api/admin/v1/admin-users")
class AdminUserController(private val repo: AdminRepository) {

    // 7.1 List admin accounts
    @GetMapping
    fun list(): Mono<ResponseEntity<Map<String, Any?>>> {
        return repo.findAll()
            .map { admin ->
                mapOf(
                    "id" to admin.id,
                    "name" to admin.name,
                    "email" to admin.email,
                    "role" to admin.role,
                    "active" to admin.active,
                    "lastLoginAt" to admin.lastLoginAt
                )
            }
            .collectList()
            .map { list ->
                ResponseEntity.ok(mapOf("success" to true, "data" to list, "error" to null))
            }
    }

    // 7.2 Create new admin
    @PostMapping
    fun create(@RequestBody reqMono: Mono<CreateAdminRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            repo.findByEmail(req.email).flatMap<ResponseEntity<Map<String, Any?>>?> { _ ->
                val body = mapOf(
                    "success" to false,
                    "data" to null,
                    "error" to mapOf("code" to "ADMIN_EXISTS", "message" to "Admin with this email already exists")
                )
                Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body))
            }.switchIfEmpty(
                Mono.defer {
                    val now = Instant.now().toString()
                    val admin = Admin(
                        id = null,
                        name = req.name,
                        email = req.email,
                        role = req.role ?: "ADMIN",
                        password = req.password, // demo only — hash in prod
                        lastLoginAt = null,
                        active = true,
                        createdAt = now,
                        updatedAt = now
                    )
                    repo.save(admin).map { created ->
                        val out = mapOf(
                            "id" to created.id,
                            "name" to created.name,
                            "email" to created.email,
                            "role" to created.role,
                            "active" to created.active,
                            "lastLoginAt" to created.lastLoginAt
                        )
                        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "data" to out, "error" to null))
                    }
                }
            )
        }
    }

    // 7.3 Update admin fields
    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody reqMono: Mono<UpdateAdminRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            repo.findById(id).flatMap { existing ->
                req.name?.let { existing.name = it }
                req.role?.let { existing.role = it }
                req.active?.let { existing.active = it }
                existing.updatedAt = Instant.now().toString()
                repo.save(existing).map { saved ->
                    val out = mapOf(
                        "id" to saved.id,
                        "name" to saved.name,
                        "email" to saved.email,
                        "role" to saved.role,
                        "active" to saved.active,
                        "lastLoginAt" to saved.lastLoginAt
                    )
                    ResponseEntity.ok(mapOf("success" to true, "data" to out, "error" to null))
                }
            }.switchIfEmpty(
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("success" to false, "data" to null, "error" to mapOf("code" to "ADMIN_NOT_FOUND", "message" to "Admin not found"))
                ))
            )
        }
    }

    // 7.4 Reset password (direct set)
    @PostMapping("/{id}/reset-password")
    fun resetPassword(@PathVariable id: String, @RequestBody reqMono: Mono<ResetPasswordRequest>): Mono<ResponseEntity<Map<String, Any?>>> {
        return reqMono.flatMap { req ->
            repo.findById(id).flatMap { existing ->
                existing.password = req.password // demo only — hash in prod
                existing.updatedAt = Instant.now().toString()
                repo.save(existing).map {
                    ResponseEntity.ok(mapOf<String, Any?>("success" to true, "data" to null, "error" to null))
                }
            }.switchIfEmpty(
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf<String, Any?>("success" to false, "data" to null, "error" to mapOf("code" to "ADMIN_NOT_FOUND", "message" to "Admin not found"))
                ))
            )
        }
    }
}
