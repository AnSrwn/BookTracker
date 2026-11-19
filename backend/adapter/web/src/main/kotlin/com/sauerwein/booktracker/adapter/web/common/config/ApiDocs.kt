package com.sauerwein.booktracker.adapter.web.common.config

/** Constants for the Swagger annotations, which only take compile-time constants. */

const val TAG_CATALOG = "Catalog"
const val TAG_MY_BOOKS = "My books"

/** The media type Spring uses for every [org.springframework.http.ProblemDetail] error body. */
const val PROBLEM_JSON = "application/problem+json"

const val PROBLEM_DETAIL_SCHEMA = "ProblemDetail"
const val PROBLEM_DETAIL_SCHEMA_REF = "#/components/schemas/$PROBLEM_DETAIL_SCHEMA"
