package ir.uciranx.ucg.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
        ResponsiveColumn(pad) {
            Hint("تغییرات از اتصال بعدی اعمال میشن.")

            // ------------------------------------------------------------ protocol
            SettingsCard("پروتکل", "روش اصلی اتصال") {
                TileGrid(Protocol.entries) { p, mod ->
                    ChoiceTile(p.title, p.hint, s.protocol == p, mod) {
                        SettingsStore.update { it.copy(protocol = p) }
                    }
                }
            }

            // ------------------------------------------------------ tor / psiphon
            if (s.protocol.warpBased) {
                SettingsCard(
                    "Tor و Psiphon",
                    "Tor یا Psiphon رو به‌عنوان خروجی یا واسط کنار تونل بذار",
                ) {
                    val helpers = Helper.entries.filter { !it.isVia || s.protocol == Protocol.MASQUE }
                    TileGrid(helpers, minTileWidth = 200) { h, mod ->
                        ChoiceTile(h.title, h.hint, s.effectiveHelper == h, mod) {
                            SettingsStore.update { it.copy(helper = h) }
                        }
                    }
                    if (s.protocol != Protocol.MASQUE) {
                        Spacer(Modifier.height(8.dp))
                        Hint("حالت «واسط» فقط روی MASQUE کار می‌کنه.")
                    }
                }
            }

            // ------------------------------------------------- protocol options
            if (s.protocol == Protocol.MASQUE) {
                SettingsCard("MASQUE") {
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
                    SubTitle("مبهم‌سازی ترافیک")
                    ChoiceChips(
                        options = MASQUE_NOIZE.map { it.first },
                        selected = s.masqueNoize,
                        label = { k -> MASQUE_NOIZE.first { it.first == k }.second },
                    ) { k -> SettingsStore.update { it.copy(masqueNoize = k) } }
                }
            }

            if (s.protocol == Protocol.WIREGUARD) {
                SettingsCard("WireGuard") {
                    SubTitle("مبهم‌سازی ترافیک")
                    ChoiceChips(
                        options = WG_NOIZE.map { it.first },
                        selected = s.wgNoize,
                        label = { k -> WG_NOIZE.first { it.first == k }.second },
                    ) { k -> SettingsStore.update { it.copy(wgNoize = k) } }
                }
            }

            // ------------------------------------------------------- scanning
            SettingsCard("پیدا کردن مسیر", "اسکن و نسخه IP") {
                if (s.protocol.warpBased) {
                    SubTitle("نوع اسکن")
                    ChoiceChips(
                        options = ScanMode.entries,
                        selected = s.scan,
                        label = { it.title },
                    ) { m -> SettingsStore.update { it.copy(scan = m) } }
                }
                SubTitle("نسخه IP")
                ChoiceChips(
                    options = IpMode.entries,
                    selected = s.ip,
                    label = { it.title },
                ) { m -> SettingsStore.update { it.copy(ip = m) } }
            }

            // -------------------------------------------------------- psiphon
            if (s.usesPsiphon) {
                SettingsCard("Psiphon") {
                    OutlinedTextField(
                        value = s.psiphonRegion,
                        onValueChange = { v ->
                            val clean = v.filter { it.isLetter() }.uppercase().take(2)
                            SettingsStore.update { it.copy(psiphonRegion = clean) }
                        },
                        label = { Text("کشور خروجی، مثل DE (خالی یعنی خودکار)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ------------------------------------------------------- advanced
            SettingsCard("پیشرفته") {
                SwitchRow(
                    "جلوگیری از نشت IPv6",
                    "ترافیک IPv6 هم وارد VPN میشه",
                    s.ipv6,
                ) { v -> SettingsStore.update { it.copy(ipv6 = v) } }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
