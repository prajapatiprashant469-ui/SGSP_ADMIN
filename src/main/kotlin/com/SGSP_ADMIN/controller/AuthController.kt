package com.SGSP_ADMIN.controller

import com.SGSP_ADMIN.repository.AdminRepository
import com.SGSP_ADMIN.util.errorPayload
import com.SGSP_ADMIN.util.successPayload
import com.SGSP_ADMIN.util.ApiResponse
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.Date

data class LoginRequest(val email: String, val password: String)

@RestController
@RequestMapping("/api/admin/v1/auth")
class AuthController {
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
}
