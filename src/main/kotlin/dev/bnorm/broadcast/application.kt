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
import kotlinx.coroutines.flow.takeWhile

@Suppress("unused") // Loaded by Ktor
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
        bearer {
            authenticate { credential ->
                when (credential.token) {
                    token -> UserIdPrincipal("admin")
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

        authenticate(optional = true) {
            post("/channels/{channelId}") {
                val channelId = call.parameters["channelId"]!!
                val data = ChannelEvent.Data(call.receiveText())

                val principal = call.principal<UserIdPrincipal>()
                if (principal == null) {
                    when (channelService.send(channelId, data)) {
                        true -> call.respond(HttpStatusCode.OK)
                        false -> call.respond(HttpStatusCode.Unauthorized)
                    }
                } else {
                    val public = call.queryParameters["public"]?.toBoolean() ?: false
                    when (channelService.sendOrCreate(channelId, data, public)) {
                        null -> call.respond(HttpStatusCode.OK)
                        else -> call.respond(HttpStatusCode.Created)
                    }
                }
            }
        }

        authenticate {
            get("/channels") {
                call.respond(channelService.getIds())
            }

            delete("/channels/{channelId}") {
                val channelId = call.parameters["channelId"]!!
                when (channelService.delete(channelId)) {
                    true -> call.respond(HttpStatusCode.OK)
                    false -> call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
