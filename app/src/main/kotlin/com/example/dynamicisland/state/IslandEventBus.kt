package com.example.dynamicisland.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object IslandEventBus {
    // BUG-018 FIX: replay=0 evita que un nuevo collector reciba estado fantasma
    private val _events = MutableSharedFlow<IslandState>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<IslandState> = _events.asSharedFlow()
    suspend fun emit(state: IslandState) { _events.emit(state) }
    fun tryEmit(state: IslandState) { _events.tryEmit(state) }
}
