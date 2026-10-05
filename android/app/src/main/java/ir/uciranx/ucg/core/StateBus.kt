package ir.uciranx.ucg.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

sealed interface VpnState {
    data object Idle : VpnState
    data class Connecting(val stage: String) : VpnState
    data class Connected(val since: Long, val label: String) : VpnState
    data object Paused : VpnState
    data class Failed(val message: String) : VpnState
}

/**
 * App-wide state and log. The log is kept in memory for the screen and also written to
 * files/logs/current.log, so it survives a crash: on the next start that file becomes
 * previous.log and the log screen can show it.
 */
object StateBus {
    private const val MAX_LINES = 3000
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    val state = MutableStateFlow<VpnState>(VpnState.Idle)

    private val buffer = ArrayDeque<String>()
    private val lines = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = lines.asStateFlow()

    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var current: File? = null
    private var previous: File? = null

    fun init(ctx: Context) {
        val dir = File(ctx.filesDir, "logs").apply { mkdirs() }
        val cur = File(dir, "current.log")
        val prev = File(dir, "previous.log")
        if (cur.isFile && cur.length() > 0) {
            prev.delete()
            cur.renameTo(prev)
        }
        cur.delete()
        current = cur
        previous = prev
    }

    fun previousLog(): List<String> = try {
        previous?.takeIf { it.isFile }?.readLines()?.takeLast(MAX_LINES) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun log(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
            lines.value = buffer.toList()
        }
        val f = current ?: return
        io.execute { append(f, line) }
    }

    /** Writes immediately, for the crash handler (the process is about to die). */
    fun logNow(line: String) {
        val f = current ?: return
        append(f, line)
    }

    private fun append(f: File, line: String) {
        try {
            if (f.length() > MAX_FILE_BYTES) f.writeText("[ucg] log trimmed\n")
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    /** Clears the screen log (the file keeps the whole session). */
    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            lines.value = emptyList()
        }
        val f = current ?: return
        io.execute { append(f, "----------------------------------------") }
    }
}
