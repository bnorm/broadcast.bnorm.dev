package dev.bnorm.broadcast

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

private const val AUTH_NAME = "BEARER"

@OptIn(ExperimentalCoroutinesApi::class)
fun Application.main() {
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
        anyHost()
    }

    install(SSE)

    val channelMutex = Mutex()
    val channels = mutableMapOf<String, MutableStateFlow<ChannelEvent>>()
    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    routing {
        sse("/channels/{channelId}/subscribe") {
            val channel = call.parameters["channelId"]!!
            val state = channels[channel]
            if (state != null) {
                heartbeat()
                state.takeWhile { it !is ChannelEvent.End }.collect {
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
                call.respond(channels.keys.toList())
            }

            post("/channels/{channelId}") {
                val channel = call.parameters["channelId"]!!
                val data = ChannelEvent.Data(call.receiveText())

                val created = channelMutex.withLock {
                    val state = channels[channel]
                    if (state != null) {
                        state.value = data
                        null
                    } else {
                        val created = MutableStateFlow<ChannelEvent>(data)
                        channels[channel] = created
                        created
                    }
                }

                if (created != null) {
                    // Automatically remove the channel if a new message hasn't been posted in 6 hours.
                    coroutineScope.launch {
                        val first = created.takeWhile { it !is ChannelEvent.End }
                            .mapLatest { delay(6.hours) }
                            .firstOrNull()

                        if (first != null) {
                            channelMutex.withLock {
                                created.value = ChannelEvent.End
                                channels.remove(channel)
                            }
                        }
                    }
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.OK)
                }
            }

            delete("/channels/{channelId}") {
                val channel = call.parameters["channelId"]!!
                channelMutex.withLock {
                    val state = channels.remove(channel)
                    if (state != null) {
                        state.value = ChannelEvent.End
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
