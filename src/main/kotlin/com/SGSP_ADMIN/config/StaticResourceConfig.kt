package com.SGSP_ADMIN.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import java.nio.file.Paths

@Configuration
class StaticResourceConfig : WebFluxConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadPath = Paths.get(System.getProperty("user.dir"), "uploads").toUri().toString()

        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadPath)
    }
}
