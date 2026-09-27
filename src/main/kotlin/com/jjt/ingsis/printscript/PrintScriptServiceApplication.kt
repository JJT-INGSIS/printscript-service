package com.jjt.ingsis.printscript

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrintScriptServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PrintScriptServiceApplication>(*args)
}
