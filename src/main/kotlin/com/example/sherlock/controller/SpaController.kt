package com.example.sherlock.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaController {
    @GetMapping("/")
    fun root(): String = "redirect:/index.html"
}
