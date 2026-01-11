package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.Admin
import com.SGSP_ADMIN.repository.AdminRepository
import com.SGSP_ADMIN.service.TokenBlacklistService
import com.SGSP_ADMIN.util.errorPayload
import com.SGSP_ADMIN.util.successPayload
import com.SGSP_ADMIN.util.ApiResponse
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

data class LoginRequest(val email: String, val password: String)

@RestController
@RequestMapping("/api/admin/v1/auth")
class AuthController(
    private val tokenBlacklistService: TokenBlacklistService,
    private val adminRepo: AdminRepository
) {
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "dev_secret_must_be_replaced_with_long_random_value_please_change"

    @PostMapping("/login")
    fun login(@RequestBody reqMono: Mono<LoginRequest>): Mono<ResponseEntity<ApiResponse<Any?>>> {
        return reqMono.flatMap { req ->
            val email = req.email.trim()
            val password = req.password ?: ""

            adminRepo.findByEmail(email)
                .flatMap { admin ->
                    if (admin.password == password) {
                        val now = Date()
                        val exp = Date(now.time + 60 * 60 * 1000) // 1 hour
                        val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
                        val token = Jwts.builder()
                            .setSubject(admin.id)
                            .claim("email", admin.email)
                            .claim("role", admin.role)
                            .setIssuedAt(now)
                            .setExpiration(exp)
                            .signWith(key)
                            .compact()

                        // update lastLoginAt and save (persist)
                        val updatedAdmin = admin.copy(lastLoginAt = Instant.now().toString())
                        adminRepo.save(updatedAdmin)
                            .map {
                                ResponseEntity.ok(
                                    successPayload(
                                        mapOf("token" to token, "admin" to updatedAdmin)
                                    )
                                )
                            }
                    } else {
                        Mono.just(ResponseEntity.status(401).body(errorPayload("INVALID_CREDENTIALS", "Email or password is incorrect")))
                    }
                }
                .switchIfEmpty(
                    Mono.just(ResponseEntity.status(401).body(errorPayload("INVALID_CREDENTIALS", "Email or password is incorrect")))
                )
                .onErrorResume { ex ->
                    Mono.just(
                        ResponseEntity.status(500).body(
                            errorPayload("INTERNAL_ERROR", ex.message ?: "Database error")
                        )
                    )
                }
        }
    }

    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): Mono<ResponseEntity<ApiResponse<Any?>>> {
        if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
            return Mono.just(ResponseEntity.ok(successPayload(null)))
        }

        val token = authorization.substringAfter("Bearer").trim()
        try {
            val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
            val claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
            val expMillis = claims.expiration?.time ?: (System.currentTimeMillis() + 60 * 60 * 1000)
            tokenBlacklistService.blacklist(token, expMillis)
        } catch (ex: Exception) {
            tokenBlacklistService.blacklist(token, System.currentTimeMillis() + 5 * 60 * 1000)
        }

        return Mono.just(ResponseEntity.ok(successPayload(null)))
    }

    @GetMapping("/me")
    fun me(exchange: ServerWebExchange): Mono<ResponseEntity<ApiResponse<Any?>>> {
        val raw = exchange.attributes["user"] ?: return Mono.just(
            ResponseEntity.status(401).body(errorPayload("AUTH_REQUIRED", "Not authenticated"))
        )

        val claims = raw as? Claims
        if (claims == null) {
            return Mono.just(ResponseEntity.status(401).body(errorPayload("AUTH_REQUIRED", "Not authenticated")))
        }

        val email = claims["email"] as? String
        val idFromClaims = claims.subject

        return if (!email.isNullOrBlank()) {
            adminRepo.findByEmail(email)
                .map { admin ->
                    val resp = mapOf(
                        "id" to admin.id,
                        "name" to admin.name,
                        "email" to admin.email,
                        "role" to admin.role,
                        "lastLoginAt" to admin.lastLoginAt
                    )
                    ResponseEntity.ok(successPayload(resp))
                }
                .switchIfEmpty(
                    Mono.just(
                        ResponseEntity.ok(
                            successPayload(
                                mapOf(
                                    "id" to (idFromClaims ?: ""),
                                    "name" to "",
                                    "email" to email,
                                    "role" to (claims["role"] ?: ""),
                                    "lastLoginAt" to Instant.now().toString()
                                )
                            )
                        )
                    )
                )
        } else {
            Mono.just(
                ResponseEntity.ok(
                    successPayload(
                        mapOf(
                            "id" to (idFromClaims ?: ""),
                            "name" to "",
                            "email" to "",
                            "role" to (claims["role"] ?: ""),
                            "lastLoginAt" to Instant.now().toString()
                        )
                    )
                )
            )
        }
    }

    @RequestMapping(value = ["/login"], method = [RequestMethod.OPTIONS])
    fun loginOptions(): ResponseEntity<Void> {
        return ResponseEntity.ok().build()
    }

}
