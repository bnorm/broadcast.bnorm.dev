package dev.bnorm.broadcast

import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    val config = CommandLineConfig(args)
    EmbeddedServer(config.rootConfig, Netty) {
        takeFrom(config.engineConfig)
    }.start(true)
}
