package com.forgeflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ForgeflowApplication

fun main(args: Array<String>) {
	runApplication<ForgeflowApplication>(*args)
}
