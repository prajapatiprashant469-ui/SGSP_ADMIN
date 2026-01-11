package com.SGSP_ADMIN.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono

@Configuration
class CorsConfig {

    @Bean
    @Order(0)
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration()

        // Accept specific origins and also allow matching patterns (useful across environments)
        // Keep these exact hosts and patterns you want to allow.
        config.allowedOrigins = listOf( // exact origins (kept for clarity)
            "http://localhost:3000",
            "http://localhost:5173",
            "https://sgsp-admin-frontend.up.railway.app"
        )
        // allow patterns as a fallback when exact matching fails (works in Spring 5.3+)
        config.allowedOriginPatterns = listOf(
            "http://localhost:*",
            "https://*.up.railway.app",
            "https://sgsp-admin-frontend.up.railway.app"
        )

        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.exposedHeaders = listOf("Authorization", "Content-Type", "Location")
        config.allowCredentials = true
        config.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        // Register for all paths
        source.registerCorsConfiguration("/**", config)

        return CorsWebFilter(source)
    }

    // Fallback preflight handler: if an OPTIONS request somehow reaches here,
    // respond with 200 and basic CORS headers so browsers get a valid preflight response.
    @Bean
    @Order(1)
    fun optionsPreflightFilter(): WebFilter {
        return WebFilter { exchange, chain ->
            if (exchange.request.method == HttpMethod.OPTIONS) {
                val resp = exchange.response
                resp.statusCode = HttpStatus.OK
                val headers = resp.headers
                val origin = exchange.request.headers.getFirst("Origin") ?: "*"
                headers.add("Access-Control-Allow-Origin", origin)
                headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
                headers.add(
                    "Access-Control-Allow-Headers",
                    exchange.request.headers.getFirst("Access-Control-Request-Headers") ?: "*"
                )
                headers.add("Access-Control-Allow-Credentials", "true")
                // THIS IS THE KEY: complete the response
                return@WebFilter resp.setComplete()
            } else {
                chain.filter(exchange)
            }
        }
    }

}
