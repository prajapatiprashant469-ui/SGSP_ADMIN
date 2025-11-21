package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.AdminRepository
import com.SGSP_ADMIN.service.TokenBlacklistService
import com.SGSP_ADMIN.util.errorPayload
import com.SGSP_ADMIN.util.successPayload
import com.SGSP_ADMIN.util.ApiResponse
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date

data class LoginRequest(val email: String, val password: String)

@RestController
@RequestMapping("/api/admin/v1/auth")
class AuthController(
    private val tokenBlacklistService: TokenBlacklistService
) {
    private val repo = AdminRepository()
    private val jwtSecret = System.getenv("JWT_SECRET") ?: "dev_secret_must_be_replaced_with_long_random_value_please_change"

    @PostMapping("/login")
    fun login(@RequestBody reqMono: Mono<LoginRequest>): Mono<ResponseEntity<ApiResponse<Any?>>> {
        return reqMono.flatMap { req ->
            repo.findByEmailAndPassword(req.email, req.password)
                .map { admin ->
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

                    ResponseEntity.ok(successPayload(mapOf("token" to token, "admin" to admin)))
                }
                .switchIfEmpty(
                    Mono.just(ResponseEntity.status(401).body(errorPayload("INVALID_CREDENTIALS", "Email or password is incorrect")))
                )
        }
    }

    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization", required = false) authorization: String?
    ): Mono<ResponseEntity<ApiResponse<Any?>>> {
        if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
            // idempotent success if no token provided or header missing
            return Mono.just(ResponseEntity.ok(successPayload(null)))
        }

        val token = authorization.substringAfter("Bearer").trim()
        try {
            val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
            val claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
            val expMillis = claims.expiration?.time ?: (System.currentTimeMillis() + 60 * 60 * 1000) // fallback 1h
            tokenBlacklistService.blacklist(token, expMillis)
        } catch (ex: Exception) {
            // malformed/expired token: still treat logout as successful, blacklist briefly to reduce replay risk
            tokenBlacklistService.blacklist(token, System.currentTimeMillis() + 5 * 60 * 1000) // 5 minutes
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

        // try to resolve full admin record from repo, fallback to claims values
        return if (!email.isNullOrBlank()) {
            repo.findByEmail(email)
                .map { admin ->
                    val resp = mapOf(
                        "id" to admin.id,
                        "name" to admin.name,
                        "email" to admin.email,
                        "role" to admin.role,
                        "lastLoginAt" to Instant.now().toString()
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
            // no email in claims — return minimal info using subject
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
}
