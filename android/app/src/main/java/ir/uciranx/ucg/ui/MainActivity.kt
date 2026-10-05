package ir.uciranx.ucg.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciranx.ucg.R
import ir.uciranx.ucg.core.StateBus
import ir.uciranx.ucg.core.UcgVpnService
import ir.uciranx.ucg.core.VpnState
import ir.uciranx.ucg.data.SettingsStore
import ir.uciranx.ucg.data.SplitMode
import kotlinx.coroutines.delay

enum class Screen { HOME, SETTINGS, APPS, DNS, LOGS }

class MainActivity : ComponentActivity() {

    private var askedNotifications = false

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                UcgVpnService.send(this, UcgVpnService.ACTION_CONNECT)
            } else {
                StateBus.state.value = VpnState.Failed("اجازه VPN داده نشد.")
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startVpn() }

    private fun onConnectPressed() {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startVpn()
    }

    private fun startVpn() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnPermission.launch(consent)
        else UcgVpnService.send(this, UcgVpnService.ACTION_CONNECT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UcgTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot(onConnect = ::onConnectPressed)
                }
            }
        }
    }
}

@Composable
fun AppRoot(onConnect: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val back = { screen = Screen.HOME }
    BackHandler(enabled = screen != Screen.HOME, onBack = back)
    when (screen) {
        Screen.HOME -> HomeScreen(onConnect) { screen = it }
        Screen.SETTINGS -> SettingsScreen(back)
        Screen.APPS -> AppsScreen(back)
        Screen.DNS -> DnsScreen(back)
        Screen.LOGS -> LogsScreen(back)
    }
}

@Composable
fun HomeScreen(onConnect: () -> Unit, open: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val st by StateBus.state.collectAsStateWithLifecycle()
    val s by SettingsStore.flow.collectAsStateWithLifecycle()

    Page("UC Guard", onBack = null, actions = {
        IconButton(onClick = { open(Screen.LOGS) }) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "لاگ")
        }
        IconButton(onClick = { open(Screen.SETTINGS) }) {
            Icon(Icons.Filled.Settings, contentDescription = "تنظیمات")
        }
    }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            PowerButton(st) {
                when (st) {
                    is VpnState.Idle, is VpnState.Failed -> onConnect()
                    else -> UcgVpnService.send(ctx, UcgVpnService.ACTION_DISCONNECT)
                }
            }
            Spacer(Modifier.height(20.dp))
            StatusText(st)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (st) {
                    is VpnState.Connected -> OutlinedButton(onClick = {
                        UcgVpnService.send(ctx, UcgVpnService.ACTION_PAUSE)
                    }) { Text("قطع موقت") }
                    is VpnState.Paused -> Button(onClick = {
                        UcgVpnService.send(ctx, UcgVpnService.ACTION_RESUME)
                    }) { Text("اتصال مجدد") }
                    else -> Unit
                }
            }

            Spacer(Modifier.height(24.dp))
            InfoCard("پروتکل", s.label()) { open(Screen.SETTINGS) }
            val split = when (s.splitMode) {
                SplitMode.OFF -> "خاموش (همه برنامه‌ها)"
                else -> "${s.splitMode.title} • ${s.selectedApps.size} برنامه"
            }
            InfoCard("تونل‌سازی برنامه‌ها", split) { open(Screen.APPS) }
            InfoCard("DNS", s.dnsServers().joinToString("  ")) { open(Screen.DNS) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PowerButton(st: VpnState, onClick: () -> Unit) {
    val target = when (st) {
        is VpnState.Connected -> UcgColors.Connected
        is VpnState.Connecting -> UcgColors.Connecting
        is VpnState.Paused -> UcgColors.Paused
        is VpnState.Failed -> UcgColors.Failed
        is VpnState.Idle -> UcgColors.Idle
    }
    val color by animateColorAsState(target, label = "power-color")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val wave by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse-scale",
    )
    Box(
        Modifier
            .size(196.dp)
            .scale(if (st is VpnState.Connecting) wave else 1f)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(3.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_power),
            contentDescription = "اتصال",
            tint = color,
            modifier = Modifier.size(78.dp),
        )
    }
}

@Composable
private fun StatusText(st: VpnState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st) {
        while (st is VpnState.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val (title, sub) = when (st) {
        is VpnState.Idle -> "قطع" to "برای اتصال دکمه رو بزن"
        is VpnState.Connecting -> "در حال اتصال..." to st.stage
        is VpnState.Connected -> "متصل" to elapsed(now - st.since)
        is VpnState.Paused -> "قطع موقت" to "ترافیک مستقیم و بدون فیلترشکن رد میشه"
        is VpnState.Failed -> "اتصال ناموفق" to st.message
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        sub,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun elapsed(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60)
}
