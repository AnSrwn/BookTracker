package com.sauerwein.booktracker.adapter.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /**
     * Swagger UI and the OpenAPI description, behind an extra password (HTTP Basic) when one is
     * configured. That password only opens the pages: calls from Swagger UI to the API under `/api/v1`
     * still need a Keycloak token, checked by [apiSecurityFilterChain]. Without a password (local development)
     * the pages stay open.
     */
    @Bean
    @Order(1)
    fun apiDocsSecurityFilterChain(
        http: HttpSecurity,
        @Value($$"${booktracker.api-docs.username:}") username: String,
        @Value($$"${booktracker.api-docs.password:}") password: String,
    ): SecurityFilterChain {
        http
            .securityMatcher(*API_DOCS_PATHS)
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

        if (password.isBlank()) {
            log.info("booktracker.api-docs.password is not set: Swagger UI and /v3/api-docs are open")
            return http.authorizeHttpRequests { it.anyRequest().permitAll() }.build()
        }
        check(username.isNotBlank()) { "booktracker.api-docs.password is set, but booktracker.api-docs.username is blank" }

        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user = User.withUsername(username).password(encoder.encode(password)).roles(API_DOCS_ROLE).build()
        return http
            .userDetailsService(InMemoryUserDetailsManager(user))
            .authorizeHttpRequests { it.anyRequest().hasRole(API_DOCS_ROLE) }
            .httpBasic(Customizer.withDefaults())
            .build()
    }

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(http: HttpSecurity, roleConverter: KeycloakRoleConverter): SecurityFilterChain {
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
        val log = LoggerFactory.getLogger(SecurityConfig::class.java)

        const val API_DOCS_ROLE = "API_DOCS"

        val API_DOCS_PATHS = arrayOf(
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
        )

        val PUBLIC_PATHS = arrayOf(
            // Health and build info (version, commit) only; /actuator/metrics needs a token
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
        )
    }
}
