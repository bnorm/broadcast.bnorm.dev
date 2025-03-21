package dev.bnorm.broadcast

sealed class ChannelEvent {
    data class Data(val data: String) : ChannelEvent()
    data object End : ChannelEvent()
}
