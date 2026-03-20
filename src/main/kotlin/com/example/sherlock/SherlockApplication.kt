package com.example.sherlock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SherlockApplication

fun main(args: Array<String>) {
	runApplication<SherlockApplication>(*args)
}
