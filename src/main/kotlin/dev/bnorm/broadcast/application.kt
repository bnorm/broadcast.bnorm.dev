package dev.bnorm.broadcast

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.takeWhile

private const val AUTH_NAME = "BEARER"

@OptIn(ExperimentalCoroutinesApi::class)
fun Application.main() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate(4)
    }
    install(CallLogging) {
        callIdMdc("call-id")
    }

    val json = DefaultJson
    install(ContentNegotiation) {
        json(json)
    }

    val token = environment.config.property("bearer.token").getString()
    install(Authentication) {
        bearer(AUTH_NAME) {
            authenticate { credential ->
                when (credential.token) {
                    token -> Unit
                    else -> null
                }
            }
        }
    }

    install(CORS) {
        allowHeader(HttpHeaders.CacheControl)
        anyHost()
    }

    install(SSE)

    val channelService = ChannelService()

    routing {
        sse("/channels/{channelId}/subscribe") {
            val channelId = call.parameters["channelId"]!!
            val channel = channelService.get(channelId)
            if (channel != null) {
                heartbeat()
                channel.takeWhile { it !is ChannelEvent.End }.collect {
                    when (it) {
                        is ChannelEvent.Data -> send(it.data)
                        ChannelEvent.End -> error("!")
                    }
                }
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        authenticate(AUTH_NAME) {
            get("/channels") {
                call.respond(channelService.getIds())
            }

            post("/channels/{channelId}") {
                val channel = call.parameters["channelId"]!!
                val data = ChannelEvent.Data(call.receiveText())
                val created = channelService.send(channel, data)
                if (created != null) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.OK)
                }
            }

            delete("/channels/{channelId}") {
                val channelId = call.parameters["channelId"]!!
                val deleted = channelService.delete(channelId)
                if (deleted != null) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
