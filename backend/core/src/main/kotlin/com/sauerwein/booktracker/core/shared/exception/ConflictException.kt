package com.sauerwein.booktracker.core.shared.exception

class ConflictException(val code: String, message: String) : RuntimeException(message)
