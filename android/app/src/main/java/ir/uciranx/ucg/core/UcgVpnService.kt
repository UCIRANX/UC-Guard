package ir.uciranx.ucg.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.uciranx.ucg.data.Settings
import ir.uciranx.ucg.data.SettingsStore
import ir.uciranx.ucg.data.SplitMode
import ir.uciranx.ucg.data.isIpv6
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UcgVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ir.uciranx.ucg.CONNECT"
        const val ACTION_DISCONNECT = "ir.uciranx.ucg.DISCONNECT"
        const val ACTION_PAUSE = "ir.uciranx.ucg.PAUSE"
        const val ACTION_RESUME = "ir.uciranx.ucg.RESUME"

        private const val MTU = 8500
        private const val TUN_V4 = "10.111.0.2"
        private const val TUN_V6 = "fd66:7563:6700::2"
        private const val MAPDNS = "198.18.0.1"

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, UcgVpnService::class.java).setAction(action)
            if (action == ACTION_CONNECT || action == ACTION_RESUME) {
                ContextCompat.startForegroundService(ctx, i)
            } else {
                ctx.startService(i)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var connectJob: Job? = null
    private var core: CoreRunner? = null
    private var tun: ParcelFileDescriptor? = null
    private var hevRunning = false
    private var foreground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> shutdown()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> connect(fresh = false)
            else -> connect(fresh = true) // our connect button, or Android's always-on VPN
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- actions

    private fun connect(fresh: Boolean) {
        if (fresh) StateBus.clear()
        setState(VpnState.Connecting("در حال آماده‌سازی..."))
        connectJob?.cancel()
        connectJob = scope.launch { runConnect() }
    }

    private fun pause() {
        if (StateBus.state.value !is VpnState.Connected && StateBus.state.value !is VpnState.Connecting) return
        connectJob?.cancel()
        scope.launch {
            teardown()
            StateBus.log("[ucg] paused: the VPN is off, apps go out directly")
            setState(VpnState.Paused)
        }
    }

    private fun shutdown() {
        connectJob?.cancel()
        scope.launch {
            teardown()
            StateBus.state.value = VpnState.Idle
            withContext(Dispatchers.Main) { leave() }
        }
    }

    // ------------------------------------------------------------ connecting

    private suspend fun runConnect() {
        val job = currentCoroutineContext()[Job]
        teardown()

        val settings = SettingsStore.current
        val plan = CoreRunner.plan(this, settings)
        val runner = CoreRunner(this) { code -> scope.launch { onCoreDied(code) } }
        synchronized(lock) { core = runner }

        try {
            runner.start(plan)
        } catch (e: Exception) {
            fail("اجرای هسته ممکن نشد: ${e.message}")
            return
        }

        setState(VpnState.Connecting("در حال پیدا کردن مسیر سالم..."))
        if (!runner.awaitPort(plan.port)) {
            if (job?.isActive == true) fail("هسته قبل از اتصال متوقف شد. صفحه لاگ رو ببین.")
            return
        }
        if (job?.isActive != true) return

        val fd = try {
            buildTun(settings, plan)
        } catch (e: Exception) {
            fail("ساخت VPN ممکن نشد: ${e.message}")
            return
        }
        if (fd == null) {
            fail("اجازه VPN داده نشده.")
            return
        }

        synchronized(lock) {
            if (job?.isActive != true) {
                fd.close()
                return
            }
            tun = fd
            val cfg = writeHevConfig(plan.port, mapDns = !plan.udpDns)
            TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)
            hevRunning = true
        }

        StateBus.log("[ucg] connected, VPN is forwarding to 127.0.0.1:${plan.port}")
        setState(VpnState.Connected(System.currentTimeMillis(), settings.label()))
    }

    private fun onCoreDied(code: Int) {
        if (StateBus.state.value is VpnState.Connected) {
            fail("اتصال قطع شد (هسته با کد $code متوقف شد).")
        }
    }

    private fun buildTun(s: Settings, plan: CoreRunner.Plan): ParcelFileDescriptor? {
        val b = Builder()
            .setSession("UC Guard")
            .setMtu(MTU)
            .addAddress(TUN_V4, 32)
            .addRoute("0.0.0.0", 0)

        if (s.ipv6) {
            b.addAddress(TUN_V6, 128)
            b.addRoute("::", 0)
        }

        if (plan.udpDns) {
            plan.dns.filter { s.ipv6 || !isIpv6(it) }.forEach { ip ->
                try {
                    b.addDnsServer(ip)
                } catch (_: Exception) {
                }
            }
        } else {
            b.addDnsServer(MAPDNS)
        }

        val me = packageName
        when (s.splitMode) {
            SplitMode.ONLY -> {
                var added = 0
                s.selectedApps.filter { it != me }.forEach {
                    try {
                        b.addAllowedApplication(it); added++
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                // Nothing valid selected: behave like "all apps".
                if (added == 0) b.addDisallowedApplication(me)
            }
            SplitMode.BYPASS -> {
                b.addDisallowedApplication(me)
                s.selectedApps.filter { it != me }.forEach {
                    try {
                        b.addDisallowedApplication(it)
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }
            // The core runs inside this app, so the app itself always stays outside the VPN.
            SplitMode.OFF -> b.addDisallowedApplication(me)
        }

        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        return b.establish()
    }

    private fun writeHevConfig(port: Int, mapDns: Boolean): File {
        val sb = StringBuilder()
        sb.append("tunnel:\n  mtu: $MTU\n")
        sb.append("socks5:\n  port: $port\n  address: 127.0.0.1\n  udp: 'udp'\n")
        if (mapDns) {
            sb.append("mapdns:\n  address: $MAPDNS\n  port: 53\n")
            sb.append("  network: 198.18.0.0\n  netmask: 255.254.0.0\n  cache-size: 10000\n")
        }
        sb.append("misc:\n  task-stack-size: 81920\n  log-level: warn\n")
        return File(cacheDir, "hev.yml").also { it.writeText(sb.toString()) }
    }

    // -------------------------------------------------------------- teardown

    private fun teardown() = synchronized(lock) {
        if (hevRunning) {
            try {
                TProxyService.TProxyStopService()
            } catch (_: Throwable) {
            }
            hevRunning = false
        }
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        core?.stop()
        core = null
    }

    private fun fail(message: String) {
        teardown()
        StateBus.log("[ucg] error: $message")
        StateBus.state.value = VpnState.Failed(message)
        scope.launch(Dispatchers.Main) { leave() }
    }

    private fun leave() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun setState(st: VpnState) {
        StateBus.state.value = st
        if (st is VpnState.Idle || st is VpnState.Failed) return
        val n = Notifications.build(this, st)
        if (!foreground) {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, Notifications.ID, n, type)
            foreground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(Notifications.ID, n)
        }
    }

    override fun onRevoke() {
        // Another VPN took over, or the user revoked us in system settings.
        shutdown()
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        val st = StateBus.state.value
        if (st !is VpnState.Failed) StateBus.state.value = VpnState.Idle
        super.onDestroy()
    }
}
