package com.sauerwein.booktracker.adapter.web.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Prefix of the project's own REST API. Springdoc's endpoints stay at the root. */
const val API_PREFIX = "/api/v1"

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(API_PREFIX) {
            it.packageName.startsWith("com.sauerwein.booktracker") &&
                    it.isAnnotationPresent(RestController::class.java)
        }
    }
}
