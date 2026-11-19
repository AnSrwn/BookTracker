package com.sauerwein.booktracker.adapter.web.common.config

import com.sauerwein.booktracker.adapter.web.common.exception.ValidationError
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.*
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
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
        .info(Info().title("BookTracker API").version("v1").description(API_DESCRIPTION))
        .tags(
            listOf(
                Tag().name(TAG_CATALOG).description(TAG_CATALOG_DESCRIPTION),
                Tag().name(TAG_MY_BOOKS).description(TAG_MY_BOOKS_DESCRIPTION),
            )
        )
        .components(
            Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, keycloakSecurityScheme())
                .apply { ModelConverters.getInstance().readAll(ValidationError::class.java).forEach(::addSchemas) }
                .addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
                .addResponses(SharedResponsesCustomizer.UNAUTHORIZED, unauthorizedResponse())
                .addResponses(SharedResponsesCustomizer.INTERNAL_SERVER_ERROR, internalServerErrorResponse())
        )
        .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME_NAME))

    @Bean
    fun sharedResponsesCustomizer(): OpenApiCustomizer = SharedResponsesCustomizer()

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

    /**
     * Written by hand: Spring's [org.springframework.http.ProblemDetail] class would show its
     * internal `properties` map, not the `code` and `errors` fields this API adds.
     */
    private fun problemDetailSchema(): Schema<Any> = ObjectSchema()
        .description("The body of every error response (RFC 9457, Problem Details).")
        .addProperty("type", StringSchema().format("uri").description("Error type. `about:blank` for all errors of this API.").example("about:blank"))
        .addProperty("title", StringSchema().description("Short name of the HTTP status.").example("Not Found"))
        .addProperty("status", IntegerSchema().description("The HTTP status code.").example(404))
        .addProperty("detail", StringSchema().description("What went wrong, readable by people.").example("Book 3f2b8c1e-5d4a-4b7e-9c1f-2a6d8e0b4c71 not found"))
        .addProperty("instance", StringSchema().format("uri").description("The path of the request that failed.").example("/api/v1/books/3f2b8c1e-5d4a-4b7e-9c1f-2a6d8e0b4c71"))
        .addProperty(
            "code",
            StringSchema().description(
                "Only on some errors: a fixed, machine-readable reason. `AlreadyInLibrary` (409) or `SourceUnavailable` (503)."
            ).example("AlreadyInLibrary")
        )
        .addProperty(
            "errors",
            ArraySchema()
                .items(Schema<Any>().`$ref`("#/components/schemas/${ValidationError::class.simpleName}"))
                .description("Only on 400 for invalid input: one entry per field that failed.")
        )

    private fun unauthorizedResponse(): ApiResponse = ApiResponse()
        .description(
            "Not logged in, or the token is invalid or expired. Log in again with **Authorize**. " +
                    "The body is empty; the `WWW-Authenticate` header says why."
        )
        .addHeaderObject("WWW-Authenticate", Header().schema(StringSchema()).description("The reason, as defined by RFC 6750."))

    private fun internalServerErrorResponse(): ApiResponse = ApiResponse()
        .description("Unexpected error in the backend. Nothing about the cause is sent; it is logged on the server.")
        .content(Content().addMediaType(PROBLEM_JSON, MediaType().schema(Schema<Any>().`$ref`(PROBLEM_DETAIL_SCHEMA_REF))))

    private companion object {
        const val SECURITY_SCHEME_NAME = "keycloak"

        val API_DESCRIPTION = """
            BookTracker is an app to keep track of your books. This API is its backend.

            ## Two kinds of books

            - **Catalog books** are shared by all users. Their data comes from public book sources.
              Nobody can change them.
            - **Private books** belong to one user. Only that user can see them. You create them
              yourself, for a book the catalog doesn't know or to keep your own version of a catalog book.

            **My books** are the books linked to you: the catalog books you added and your private books.

            ## Typical flow

            1. Find the book: search by title, author or ISBN (`GET /api/v1/catalog/search`),
               or scan its barcode and look up the ISBN (`GET /api/v1/catalog/isbn/{isbn}`).
            2. A hit with `kind` = `CATALOG` → add it with `POST /api/v1/books/catalog/{id}`.
            3. No matching hit, or a hit without an `id` → create a private book with `POST /api/v1/books/private`.
            4. List your books with `GET /api/v1/books`.

            ## Logging in

            Every endpoint needs a login. Click **Authorize** and log in with your BookTracker account.
            Swagger UI then sends the token with each request.

            ## Errors

            Errors come as Problem Details (RFC 9457, `application/problem+json`) with `status` and `detail`.
            Some add a fixed `code` (for example `AlreadyInLibrary`) or, for invalid input, a list of `errors` per field.
            A missing or expired login (401) is the exception: it has an empty body.
        """.trimIndent()

        val TAG_CATALOG_DESCRIPTION = """
            Find books: in the shared catalog and in public book sources. Your own private books show up
            in text search too. Searching never changes your books.
        """.trimIndent()

        val TAG_MY_BOOKS_DESCRIPTION = """
            Your books: list and read them, add catalog books, and create private books.
        """.trimIndent()
    }
}
