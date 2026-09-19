package com.sauerwein.booktracker.adapter.web.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix("/api/v1") {
            it.packageName.startsWith("com.sauerwein.booktracker") &&
                    it.isAnnotationPresent(RestController::class.java)
        }
    }
}
