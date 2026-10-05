package ir.uciranx.ucg.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciranx.ucg.data.Helper
import ir.uciranx.ucg.data.IpMode
import ir.uciranx.ucg.data.MASQUE_NOIZE
import ir.uciranx.ucg.data.Protocol
import ir.uciranx.ucg.data.ScanMode
import ir.uciranx.ucg.data.SettingsStore
import ir.uciranx.ucg.data.WG_NOIZE

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by SettingsStore.flow.collectAsStateWithLifecycle()

    Page("تنظیمات اتصال", onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Hint("تغییرات از اتصال بعدی اعمال میشن.")

            Section("پروتکل")
            Protocol.entries.forEach { p ->
                RadioRow(p.title, p.hint, s.protocol == p) {
                    SettingsStore.update { it.copy(protocol = p) }
                }
            }

            if (s.protocol.warpBased) {
                Section("Tor و Psiphon")
                Hint("می‌تونی Tor یا Psiphon رو به‌عنوان خروجی یا واسط کنار تونل بذاری.")
                Helper.entries
                    .filter { !it.isVia || s.protocol == Protocol.MASQUE }
                    .forEach { h ->
                        RadioRow(h.title, h.hint, s.effectiveHelper == h) {
                            SettingsStore.update { it.copy(helper = h) }
                        }
                    }
                if (s.protocol != Protocol.MASQUE) {
                    Hint("حالت «واسط» فقط روی MASQUE کار می‌کنه.")
                }
            }

            if (s.protocol == Protocol.MASQUE) {
                Section("MASQUE")
                SwitchRow(
                    "HTTP/2 به‌جای HTTP/3",
                    "وقتی UDP بسته‌ست یا خیلی کنده",
                    s.masqueHttp2,
                ) { v -> SettingsStore.update { it.copy(masqueHttp2 = v) } }
                if (s.masqueHttp2) {
                    SwitchRow(
                        "تکه‌تکه کردن ClientHello",
                        "وقتی خود HTTP/2 هم بسته میشه",
                        s.fragment,
                    ) { v -> SettingsStore.update { it.copy(fragment = v) } }
                }
                Hint("مبهم‌سازی ترافیک")
                MASQUE_NOIZE.forEach { (key, title) ->
                    RadioRow(title, null, s.masqueNoize == key) {
                        SettingsStore.update { it.copy(masqueNoize = key) }
                    }
                }
            }

            if (s.protocol == Protocol.WIREGUARD) {
                Section("WireGuard")
                Hint("مبهم‌سازی ترافیک")
                WG_NOIZE.forEach { (key, title) ->
                    RadioRow(title, null, s.wgNoize == key) {
                        SettingsStore.update { it.copy(wgNoize = key) }
                    }
                }
            }

            if (s.protocol.warpBased) {
                Section("نوع اسکن")
                ScanMode.entries.forEach { m ->
                    RadioRow(m.title, null, s.scan == m) {
                        SettingsStore.update { it.copy(scan = m) }
                    }
                }
            }

            Section("نسخه IP برای پیدا کردن مسیر")
            IpMode.entries.forEach { m ->
                RadioRow(m.title, null, s.ip == m) { SettingsStore.update { it.copy(ip = m) } }
            }

            if (s.usesPsiphon) {
                Section("Psiphon")
                OutlinedTextField(
                    value = s.psiphonRegion,
                    onValueChange = { v ->
                        val clean = v.filter { it.isLetter() }.uppercase().take(2)
                        SettingsStore.update { it.copy(psiphonRegion = clean) }
                    },
                    label = { Text("کشور خروجی، مثل DE (خالی یعنی خودکار)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("پیشرفته")
            OutlinedTextField(
                value = s.exitLoc,
                onValueChange = { v -> SettingsStore.update { it.copy(exitLoc = v.uppercase()) } },
                label = { Text("کشورهای خروجی مجاز، مثل DE,NL یا !IR") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("خالی بذاری یعنی هر کشوری قبوله.")
            SwitchRow(
                "جلوگیری از نشت IPv6",
                "ترافیک IPv6 هم وارد VPN میشه",
                s.ipv6,
            ) { v -> SettingsStore.update { it.copy(ipv6 = v) } }

            Spacer(Modifier.height(32.dp))
        }
    }
}
