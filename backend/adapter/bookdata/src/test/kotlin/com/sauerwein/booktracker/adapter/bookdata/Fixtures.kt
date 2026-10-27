package com.sauerwein.booktracker.adapter.bookdata

fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()
