package com.SGSP_ADMIN.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration()

        // Allowed origins for local dev and deployed frontend (including requested railway origin)
        config.allowedOrigins = listOf(
            "http://localhost:3000",
            "http://localhost:5173",
            "https://sgsp-admin-frontend.up.railway.app"
        )

        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.exposedHeaders = listOf("Authorization", "Content-Type", "Location")
        config.allowCredentials = true
        config.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)

        return CorsWebFilter(source)
    }
}
