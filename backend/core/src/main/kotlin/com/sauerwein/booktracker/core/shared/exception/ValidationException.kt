package com.sauerwein.booktracker.core.shared.exception

class ValidationException(val field: String, val code: String, message: String) : RuntimeException(message)
