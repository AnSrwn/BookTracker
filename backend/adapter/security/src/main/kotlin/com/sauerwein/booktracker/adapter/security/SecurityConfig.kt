package com.sauerwein.booktracker.adapter.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, roleConverter: KeycloakRoleConverter): SecurityFilterChain {
        return http
            .csrf { it.disable() } // Bearer-only auth, no session cookie: nothing a foreign site could make the browser send.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // For these paths, no token is needed
                it.requestMatchers(*PUBLIC_PATHS).permitAll()

                // For everything else, a token is needed
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.jwtAuthenticationConverter(roleConverter) }
            }
            .build()
    }

    private companion object {
        val PUBLIC_PATHS = arrayOf(
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            // Health only; /actuator/metrics needs a token
            "/actuator/health",
            "/actuator/health/**",
        )
    }
}
