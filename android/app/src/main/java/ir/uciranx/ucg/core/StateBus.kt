package ir.uciranx.ucg.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Idle : VpnState
    data class Connecting(val stage: String) : VpnState
    data class Connected(val since: Long, val label: String) : VpnState
    data object Paused : VpnState
    data class Failed(val message: String) : VpnState
}

object StateBus {
    private const val MAX_LINES = 1500

    val state = MutableStateFlow<VpnState>(VpnState.Idle)

    private val buffer = ArrayDeque<String>()
    private val lines = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = lines.asStateFlow()

    fun log(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
            lines.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            lines.value = emptyList()
        }
    }
}
