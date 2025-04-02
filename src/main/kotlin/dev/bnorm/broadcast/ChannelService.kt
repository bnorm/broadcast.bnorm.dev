package dev.bnorm.broadcast

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelService {
    private val channelMutex = Mutex()
    private val channels = mutableMapOf<String, MutableStateFlow<ChannelEvent>>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(channelId: String): StateFlow<ChannelEvent>? {
        return channels[channelId]
    }

    fun getIds(): Set<String> {
        return channels.keys
    }

    suspend fun send(channelId: String, data: ChannelEvent): StateFlow<ChannelEvent>? {
        val created = channelMutex.withLock {
            val state = channels[channelId]
            if (state != null) {
                state.value = data
                return null
            }

            val created = MutableStateFlow(data)
            channels[channelId] = created
            created
        }

        // Automatically remove the channel if a new message hasn't been posted in 6 hours.
        coroutineScope.launch {
            val first = created.takeWhile { it !is ChannelEvent.End }
                .mapLatest { delay(6.hours) }
                .firstOrNull()
            if (first != null) delete(channelId)
        }

        return created
    }

    suspend fun delete(channelId: String): StateFlow<ChannelEvent>? {
        channelMutex.withLock {
            val channel = channels.remove(channelId)
            if (channel != null) {
                channel.value = ChannelEvent.End
                return channel
            } else {
                return null
            }
        }
    }
}
