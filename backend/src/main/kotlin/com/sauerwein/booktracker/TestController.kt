package com.sauerwein.booktracker

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController {

    @GetMapping("/")
    fun index(@RequestParam("name") name: String) = "Hello, $name!"

}