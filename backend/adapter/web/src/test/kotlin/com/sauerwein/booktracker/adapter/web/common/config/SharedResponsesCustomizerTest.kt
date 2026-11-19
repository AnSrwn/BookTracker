package com.sauerwein.booktracker.adapter.web.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SharedResponsesCustomizerTest {

    private val customizer = SharedResponsesCustomizer()

    @Test
    fun `adds 401 and 500 to every operation`() {
        val get = Operation().responses(ApiResponses().addApiResponse("200", ApiResponse().description("OK")))
        val post = Operation()
        val openApi = OpenAPI().paths(
            Paths()
                .addPathItem("/books", PathItem().get(get))
                .addPathItem("/books/private", PathItem().post(post))
        )

        customizer.customise(openApi)

        assertEquals(setOf("200", "401", "500"), get.responses.keys)
        assertEquals(setOf("401", "500"), post.responses.keys)
        assertEquals("#/components/responses/Unauthorized", get.responses["401"]!!.`$ref`)
        assertEquals("#/components/responses/InternalServerError", get.responses["500"]!!.`$ref`)
    }

    @Test
    fun `keeps a response the operation documents itself`() {
        val own401 = ApiResponse().description("Own text")
        val operation = Operation().responses(ApiResponses().addApiResponse("401", own401))
        val openApi = OpenAPI().paths(Paths().addPathItem("/books", PathItem().get(operation)))

        customizer.customise(openApi)

        assertSame(own401, operation.responses["401"])
    }

    @Test
    fun `does nothing without paths`() {
        customizer.customise(OpenAPI())
    }
}
