package ir.uciranx.ucg.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Protocol(val title: String, val hint: String) {
    MASQUE("MASQUE", "پیشنهادی. شبیه ترافیک عادی HTTPS"),
    WIREGUARD("WireGuard", "سریع و سبک، برای شبکه‌های کم‌سخت‌گیر"),
    GOOL("Gool", "WireGuard داخل MASQUE، با آی‌پی خارجی"),
    GOOL_CLASSIC("Gool کلاسیک", "WireGuard داخل WireGuard"),
    MIM("MASQUE در MASQUE", "دو لایه MASQUE روی هم"),
    TOR("Tor", "شبکه Tor به‌صورت مستقل"),
    PSIPHON("Psiphon", "سایفون به‌صورت مستقل");

    val warpBased: Boolean get() = this != TOR && this != PSIPHON
}

enum class Helper(val title: String, val hint: String) {
    NONE("هیچ", "فقط خود پروتکل"),
    TOR_EXIT("خروجی از Tor", "اول تونل وصل میشه، بعد ترافیک از Tor خارج میشه"),
    PSIPHON_EXIT("خروجی از Psiphon", "اول تونل وصل میشه، بعد ترافیک از Psiphon خارج میشه"),
    TOR_VIA("واسط Tor", "اول Tor وصل میشه و تونل از داخلش برقرار میشه"),
    PSIPHON_VIA("واسط Psiphon", "اول Psiphon وصل میشه و تونل از داخلش برقرار میشه");

    val isVia: Boolean get() = this == TOR_VIA || this == PSIPHON_VIA
}

enum class ScanMode(val flag: String, val title: String) {
    TURBO("turbo", "سریع (turbo)"),
    BALANCED("balanced", "متعادل (balanced)"),
    THOROUGH("thorough", "دقیق (thorough)"),
    STEALTH("stealth", "آرام و کم‌سروصدا (stealth)"),
    IRONCLAD("ironclad", "مطمئن‌ترین، کندترین (ironclad)"),
}

enum class IpMode(val flag: String, val title: String) {
    V4("-4", "IPv4"),
    V6("-6", "IPv6"),
    DUAL("--dual", "هر دو"),
}

enum class SplitMode(val title: String, val hint: String) {
    OFF("خاموش", "همه برنامه‌ها از فیلترشکن رد میشن"),
    ONLY("فقط تیک‌خورده‌ها", "فقط برنامه‌هایی که تیک زدی از فیلترشکن رد میشن"),
    BYPASS("همه به جز تیک‌خورده‌ها", "برنامه‌های تیک‌خورده مستقیم و بدون فیلترشکن وصل میشن"),
}

data class DnsPreset(val name: String, val ip: String)

val DNS_PRESETS = listOf(
    DnsPreset("Cloudflare", "1.1.1.1"),
    DnsPreset("Cloudflare", "1.0.0.1"),
    DnsPreset("Google", "8.8.8.8"),
    DnsPreset("Google", "8.8.4.4"),
    DnsPreset("Quad9", "9.9.9.9"),
    DnsPreset("Quad9", "149.112.112.112"),
    DnsPreset("AdGuard", "94.140.14.14"),
    DnsPreset("OpenDNS", "208.67.222.222"),
)

val MASQUE_NOIZE = listOf(
    "firewall" to "firewall (پیشنهادی)",
    "gfw" to "gfw (سنگین‌تر)",
    "off" to "خاموش",
)

val WG_NOIZE = listOf(
    "balanced" to "balanced (پیشنهادی)",
    "aggressive" to "aggressive (سنگین‌ترین)",
    "light" to "light (سبک)",
    "off" to "خاموش",
)

private val IPV4 = Regex(
    """^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$"""
)
private val IPV6 = Regex("^[0-9a-fA-F:]+$")

fun isValidIp(raw: String): Boolean {
    val t = raw.trim()
    if (IPV4.matches(t)) return true
    return t.count { it == ':' } >= 2 && t.length <= 39 && IPV6.matches(t)
}

fun isIpv6(ip: String) = ip.contains(':')

data class Settings(
    val protocol: Protocol = Protocol.MASQUE,
    val helper: Helper = Helper.NONE,
    val scan: ScanMode = ScanMode.BALANCED,
    val ip: IpMode = IpMode.V4,
    val masqueHttp2: Boolean = false,
    val fragment: Boolean = false,
    val masqueNoize: String = "firewall",
    val wgNoize: String = "balanced",
    val psiphonRegion: String = "",
    val exitLoc: String = "",
    val ipv6: Boolean = true,
    val splitMode: SplitMode = SplitMode.OFF,
    val selectedApps: Set<String> = emptySet(),
    val dnsEnabled: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    val customDns: List<String> = emptyList(),
) {
    /** Helper that really applies to the chosen protocol. */
    val effectiveHelper: Helper
        get() = when {
            !protocol.warpBased -> Helper.NONE
            helper.isVia && protocol != Protocol.MASQUE -> Helper.NONE
            else -> helper
        }

    val usesTor: Boolean
        get() = protocol == Protocol.TOR ||
            effectiveHelper == Helper.TOR_EXIT || effectiveHelper == Helper.TOR_VIA

    val usesPsiphon: Boolean
        get() = protocol == Protocol.PSIPHON ||
            effectiveHelper == Helper.PSIPHON_EXIT || effectiveHelper == Helper.PSIPHON_VIA

    /** Traffic leaves through Psiphon's own proxy, which resolves names itself. */
    val psiphonResolves: Boolean
        get() = protocol == Protocol.PSIPHON || effectiveHelper == Helper.PSIPHON_EXIT

    fun dnsServers(): List<String> = dnsEnabled.distinct().ifEmpty { listOf("1.1.1.1") }

    fun label(): String {
        val h = effectiveHelper
        return if (h == Helper.NONE) protocol.title else "${protocol.title} + ${h.title}"
    }

    fun toJson(): String = JSONObject().apply {
        put("protocol", protocol.name)
        put("helper", helper.name)
        put("scan", scan.name)
        put("ip", ip.name)
        put("masqueHttp2", masqueHttp2)
        put("fragment", fragment)
        put("masqueNoize", masqueNoize)
        put("wgNoize", wgNoize)
        put("psiphonRegion", psiphonRegion)
        put("exitLoc", exitLoc)
        put("ipv6", ipv6)
        put("splitMode", splitMode.name)
        put("selectedApps", JSONArray(selectedApps.toList()))
        put("dnsEnabled", JSONArray(dnsEnabled))
        put("customDns", JSONArray(customDns))
    }.toString()

    companion object {
        private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, def: T): T =
            optString(key, def.name).let { v -> enumValues<T>().firstOrNull { it.name == v } ?: def }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }

        fun fromJson(raw: String?): Settings {
            if (raw.isNullOrBlank()) return Settings()
            return try {
                val o = JSONObject(raw)
                val d = Settings()
                Settings(
                    protocol = o.enumOr("protocol", d.protocol),
                    helper = o.enumOr("helper", d.helper),
                    scan = o.enumOr("scan", d.scan),
                    ip = o.enumOr("ip", d.ip),
                    masqueHttp2 = o.optBoolean("masqueHttp2", d.masqueHttp2),
                    fragment = o.optBoolean("fragment", d.fragment),
                    masqueNoize = o.optString("masqueNoize", d.masqueNoize),
                    wgNoize = o.optString("wgNoize", d.wgNoize),
                    psiphonRegion = o.optString("psiphonRegion", ""),
                    exitLoc = o.optString("exitLoc", ""),
                    ipv6 = o.optBoolean("ipv6", d.ipv6),
                    splitMode = o.enumOr("splitMode", d.splitMode),
                    selectedApps = o.optJSONArray("selectedApps").strings().toSet(),
                    dnsEnabled = if (o.has("dnsEnabled")) o.optJSONArray("dnsEnabled").strings() else d.dnsEnabled,
                    customDns = o.optJSONArray("customDns").strings(),
                )
            } catch (_: Exception) {
                Settings()
            }
        }
    }
}

object SettingsStore {
    private const val KEY = "settings"
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("ucg", Context.MODE_PRIVATE)
        prefs = p
        state.value = Settings.fromJson(p.getString(KEY, null))
    }

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        state.value = next
        prefs?.edit()?.putString(KEY, next.toJson())?.apply()
    }
}
