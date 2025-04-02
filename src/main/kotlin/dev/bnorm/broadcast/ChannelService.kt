package dev.bnorm.broadcast

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelService {
    private class Channel(
        val state: MutableStateFlow<ChannelEvent>,
        val public: Boolean = false,
    )

    private val channelMutex = Mutex()
    private val channels = mutableMapOf<String, Channel>()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(channelId: String): StateFlow<ChannelEvent>? {
        return channels[channelId]?.state
    }

    fun getIds(): Set<String> {
        return channels.keys
    }

    fun send(channelId: String, data: ChannelEvent): Boolean {
        val channel = channels[channelId]
        if (channel != null && channel.public) {
            channel.state.value = data
            return true
        } else {
            return false
        }
    }

    suspend fun sendOrCreate(channelId: String, data: ChannelEvent, public: Boolean = false): StateFlow<ChannelEvent>? {
        val created = channelMutex.withLock {
            val channel = channels[channelId]
            if (channel != null) {
                channel.state.value = data
                return null
            }

            val created = MutableStateFlow(data)
            channels[channelId] = Channel(created, public)
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

    suspend fun delete(channelId: String): Boolean {
        channelMutex.withLock {
            val channel = channels.remove(channelId)
            if (channel != null) {
                channel.state.value = ChannelEvent.End
                return true
            } else {
                return false
            }
        }
    }
}
