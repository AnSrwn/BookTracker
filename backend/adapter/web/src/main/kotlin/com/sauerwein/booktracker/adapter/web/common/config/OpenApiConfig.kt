package com.sauerwein.booktracker.adapter.web.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @param:Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,
) {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("BookTracker API").version("v1"))
        .components(Components().addSecuritySchemes(SECURITY_SCHEME_NAME, keycloakSecurityScheme()))
        .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))

    private fun keycloakSecurityScheme(): SecurityScheme = SecurityScheme()
        .type(SecurityScheme.Type.OAUTH2)
        .description("Keycloak realm login. The Swagger UI uses authorization code + PKCE.")
        .flows(
            OAuthFlows().authorizationCode(
                OAuthFlow()
                    .authorizationUrl("$issuerUri/protocol/openid-connect/auth")
                    .tokenUrl("$issuerUri/protocol/openid-connect/token")
                    .scopes(
                        Scopes()
                            .addString("openid", "Issue an ID token")
                            .addString("profile", "Name and username")
                            .addString("email", "Email address")
                    )
            )
        )

    private companion object {
        const val SECURITY_SCHEME_NAME = "keycloak"
    }
}
