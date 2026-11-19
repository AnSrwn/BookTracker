package com.sauerwein.booktracker.adapter.web.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OpenApiCustomizer

/**
 * Adds the responses every endpoint can give, so the API interfaces document only their own.
 * A response an endpoint already documents for the same status is kept.
 */
class SharedResponsesCustomizer : OpenApiCustomizer {

    override fun customise(openApi: OpenAPI) {
        openApi.paths.orEmpty().values
            .flatMap { it.readOperations() }
            .forEach { operation ->
                val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
                SHARED.forEach { (status, name) ->
                    responses.putIfAbsent(status, ApiResponse().`$ref`("#/components/responses/$name"))
                }
            }
    }

    companion object {
        /** Names of the responses in `components.responses`, registered by [OpenApiConfig]. */
        const val UNAUTHORIZED = "Unauthorized"
        const val INTERNAL_SERVER_ERROR = "InternalServerError"

        private val SHARED = mapOf("401" to UNAUTHORIZED, "500" to INTERNAL_SERVER_ERROR)
    }
}
