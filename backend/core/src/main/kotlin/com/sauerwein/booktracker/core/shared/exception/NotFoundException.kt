package com.sauerwein.booktracker.core.shared.exception

class NotFoundException(entity: String, id: Any) : RuntimeException("$entity $id not found")
