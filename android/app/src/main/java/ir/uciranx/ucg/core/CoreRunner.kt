package ir.uciranx.ucg.core

import android.content.Context
import ir.uciranx.ucg.data.Helper
import ir.uciranx.ucg.data.Protocol
import ir.uciranx.ucg.data.Settings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the Aether core (shipped inside the APK as libaether.so) as a child process
 * and streams its output into the in-app log.
 */
class CoreRunner(private val ctx: Context, private val onExit: (Int) -> Unit) {

    data class Plan(
        val args: List<String>,
        /** SOCKS5 port the VPN must forward to. */
        val port: Int,
        /** DNS over UDP works on this listener; otherwise names are mapped and resolved remotely. */
        val udpDns: Boolean,
        val dns: List<String>,
    )

    companion object {
        const val PORT_TUNNEL = 10819
        const val PORT_TOR = 10820
        const val PORT_PSIPHON = 10821

        private const val BIN_CORE = "libaether.so"
        private const val BIN_PSIPHON = "libpsiphon-tunnel-core.so"
        private const val BIN_LYREBIRD = "liblyrebird.so"

        private val ANSI = Regex("\u001B\\[[0-9;?]*[A-Za-z]")

        fun plan(ctx: Context, s: Settings): Plan {
            val lib = ctx.applicationInfo.nativeLibraryDir
            val files = ctx.filesDir
            val helper = s.effectiveHelper
            val dns = s.dnsServers()

            val a = mutableListOf("$lib/$BIN_CORE")
            a += listOf("--bind", "127.0.0.1:$PORT_TUNNEL")
            a += listOf("--config", File(files, "aether.toml").absolutePath)
            a += "--quick-reconnect"
            a += s.ip.flag
            a += listOf("--dns", dns.joinToString(","))
            if (s.protocol.warpBased) a += listOf("--scan", s.scan.flag)

            when (s.protocol) {
                Protocol.MASQUE -> {
                    a += "--masque"
                    if (s.masqueHttp2) {
                        a += "--h2"
                        if (s.fragment) a += "--fragment"
                    }
                    a += listOf("--noize", s.masqueNoize)
                }
                Protocol.WIREGUARD -> a += listOf("--wg", "--noize", s.wgNoize)
                Protocol.GOOL -> a += "--gool"
                Protocol.GOOL_CLASSIC -> a += "--gool-classic"
                Protocol.MIM -> a += "--mim"
                Protocol.TOR -> a += "--tor-only"
                Protocol.PSIPHON -> a += "--psiphon-only"
            }

            when (helper) {
                Helper.TOR_EXIT -> a += "--tor"
                Helper.TOR_VIA -> a += "--tor-reverse"
                Helper.PSIPHON_EXIT -> a += "--psiphon"
                Helper.PSIPHON_VIA -> a += "--psiphon-reverse"
                Helper.NONE -> Unit
            }

            if (s.usesTor) {
                a += listOf("--tor-bind", "127.0.0.1:$PORT_TOR")
                a += listOf("--tor-dir", File(files, "tor").absolutePath)
                val lyrebird = File(lib, BIN_LYREBIRD)
                if (lyrebird.isFile) {
                    val p = lyrebird.absolutePath
                    a += listOf("--tor-pt", "obfs4=$p;webtunnel=$p;snowflake=$p;meek_lite=$p")
                }
            }

            if (s.usesPsiphon) {
                a += listOf("--psiphon-bind", "127.0.0.1:$PORT_PSIPHON")
                a += listOf("--psiphon-dir", File(files, "psiphon").absolutePath)
                a += listOf("--psiphon-bin", File(lib, BIN_PSIPHON).absolutePath)
                if (s.psiphonRegion.isNotBlank()) {
                    a += listOf("--psiphon-region", s.psiphonRegion.trim().uppercase())
                }
            }

            if (s.exitLoc.isNotBlank()) a += listOf("--exit-loc", s.exitLoc.trim())

            val port = when {
                s.protocol == Protocol.TOR || helper == Helper.TOR_EXIT -> PORT_TOR
                s.protocol == Protocol.PSIPHON || helper == Helper.PSIPHON_EXIT -> PORT_PSIPHON
                else -> PORT_TUNNEL
            }

            return Plan(a, port, udpDns = !s.psiphonResolves, dns = dns)
        }

        /** Kills any helper process of ours left over from an earlier run (psiphon, lyrebird, core). */
        fun killStrays(ctx: Context) {
            val dir = ctx.applicationInfo.nativeLibraryDir
            val me = android.os.Process.myPid()
            File("/proc").listFiles()?.forEach { f ->
                val pid = f.name.toIntOrNull() ?: return@forEach
                if (pid == me) return@forEach
                val cmd = try {
                    File(f, "cmdline").readText()
                } catch (_: Exception) {
                    return@forEach
                }
                if (cmd.startsWith(dir)) {
                    try {
                        android.os.Process.sendSignal(pid, 9)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false

    val alive: Boolean get() = process?.isAlive == true

    fun start(plan: Plan) {
        killStrays(ctx)
        // A core left over from a crash may still hold the port for a moment; wait for it
        // to go away, otherwise its dying listener would be mistaken for the new one.
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline &&
            (probe(plan.port) || probe(PORT_TUNNEL))
        ) {
            Thread.sleep(150)
        }
        File(ctx.filesDir, "tor").mkdirs()
        File(ctx.filesDir, "psiphon").mkdirs()

        val pb = ProcessBuilder(plan.args)
            .directory(ctx.filesDir)
            .redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", ctx.filesDir.absolutePath)
            put("TMPDIR", ctx.cacheDir.absolutePath)
            put("NO_COLOR", "1")
            put("RUST_LOG_STYLE", "never")
            put("RUST_BACKTRACE", "1")
        }

        StateBus.log("[ucg] " + plan.args.drop(1).joinToString(" "))
        val p = pb.start()
        process = p

        thread(name = "aether-log", isDaemon = true) {
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    StateBus.log(line.replace(ANSI, ""))
                }
            } catch (_: IOException) {
            }
            val code = try {
                p.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            StateBus.log("[ucg] core exited (code $code)")
            if (!stopping) onExit(code)
        }
    }

    /** Waits until the SOCKS5 listener answers. Returns false if the core died first. */
    suspend fun awaitPort(port: Int): Boolean {
        while (currentCoroutineContext().isActive) {
            if (!alive) return false
            if (probe(port)) return true
            delay(400)
        }
        return false
    }

    private fun probe(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    } catch (_: IOException) {
        false
    }

    fun stop() {
        stopping = true
        val p = process
        process = null
        if (p != null) {
            try {
                p.destroy()
                if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) p.destroyForcibly()
            } catch (_: Exception) {
            }
        }
        killStrays(ctx)
    }
}
