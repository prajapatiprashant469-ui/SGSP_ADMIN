package com.SGSP_ADMIN.security

import com.SGSP_ADMIN.service.TokenBlacklistService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class JwtAuthFilter(
    private val tokenBlacklistService: TokenBlacklistService,
    @Value("\${JWT_SECRET:dev_secret_must_be_replaced_with_long_random_value_please_change}") private val jwtSecret: String
) : WebFilter {
    private val mapper = ObjectMapper().registerKotlinModule()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val auth = exchange.request.headers.getFirst("Authorization") ?: ""
        if (auth.isBlank()) {
            // no token present — allow anonymous requests (login/public routes)
            return chain.filter(exchange)
        }

        val parts = auth.split(" ")
        if (parts.size != 2 || parts[0] != "Bearer") {
            // malformed header — reject
            return writeError(exchange, "AUTH_REQUIRED", "Authorization header missing or malformed", HttpStatus.UNAUTHORIZED)
        }
        val token = parts[1]

        // Check blacklist first
        if (tokenBlacklistService.isBlacklisted(token)) {
            return writeError(exchange, "TOKEN_BLACKLISTED", "Token was revoked", HttpStatus.UNAUTHORIZED)
        }

        return try {
            val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
            val claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
            exchange.attributes["user"] = claims
            chain.filter(exchange)
        } catch (ex: Exception) {
            writeError(exchange, "INVALID_TOKEN", "Token is invalid or expired", HttpStatus.UNAUTHORIZED)
        }
    }

    private fun writeError(exchange: ServerWebExchange, code: String, message: String?, status: HttpStatus): Mono<Void> {
        val body = mapOf("success" to false, "data" to null, "error" to mapOf("code" to code, "message" to message))
        val bytes = mapper.writeValueAsBytes(body)
        exchange.response.statusCode = status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        return exchange.response.writeWith(Mono.just(exchange.response.bufferFactory().wrap(bytes)))
    }
}
