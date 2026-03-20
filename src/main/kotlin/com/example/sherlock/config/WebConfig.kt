package com.example.sherlock.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/browser/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    if (resourcePath.isEmpty()) {
                        val index = ClassPathResource("static/browser/index.html")
                        return if (index.exists()) index else null
                    }
                    if (resourcePath.startsWith("api/") ||
                        resourcePath.startsWith("swagger-ui") ||
                        resourcePath.startsWith("v3/") ||
                        resourcePath.startsWith("api-docs")) {
                        return null
                    }
                    val requested = ClassPathResource("static/browser/$resourcePath")
                    if (requested.exists()) return requested
                    val index = ClassPathResource("static/browser/index.html")
                    return if (index.exists()) index else null
                }
            })
    }
}
