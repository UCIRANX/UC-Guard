package ir.uciranx.ucg.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciranx.ucg.data.DNS_PRESETS
import ir.uciranx.ucg.data.SettingsStore
import ir.uciranx.ucg.data.isValidIp

private fun setDns(ip: String, on: Boolean) = SettingsStore.update {
    it.copy(dnsEnabled = if (on) (it.dnsEnabled + ip).distinct() else it.dnsEnabled - ip)
}

@Composable
fun DnsScreen(onBack: () -> Unit) {
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun add() {
        val ip = input.trim()
        if (!isValidIp(ip)) {
            error = "آدرس IP معتبر نیست"
            return
        }
        SettingsStore.update {
            val custom = if (ip in it.customDns || DNS_PRESETS.any { p -> p.ip == ip }) it.customDns else it.customDns + ip
            it.copy(customDns = custom, dnsEnabled = (it.dnsEnabled + ip).distinct())
        }
        input = ""
        error = null
    }

    Page("DNS", onBack) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Hint("هر چندتا خواستی تیک بزن. همه درخواست‌های DNS از داخل تونل فرستاده میشن.")
            if (s.psiphonResolves) {
                Hint("توی حالت Psiphon، خود Psiphon اسم سایت‌ها رو پیدا می‌کنه و این DNSها استفاده نمیشن.")
            }

            Section("پیش‌فرض‌ها")
            DNS_PRESETS.forEach { p ->
                CheckRow(p.ip, p.name, p.ip in s.dnsEnabled) { setDns(p.ip, it) }
            }

            Section("DNS دستی")
            if (s.customDns.isEmpty()) Hint("هنوز چیزی اضافه نکردی.")
            s.customDns.forEach { ip ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ip in s.dnsEnabled, onCheckedChange = { setDns(ip, it) })
                    Text(ip, Modifier.weight(1f))
                    IconButton(onClick = {
                        SettingsStore.update { it.copy(customDns = it.customDns - ip, dnsEnabled = it.dnsEnabled - ip) }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim(); error = null },
                    label = { Text("مثلا 9.9.9.9") },
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = ::add) { Text("افزودن") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp))
            }
            if (s.dnsEnabled.isEmpty()) {
                Hint("هیچ DNSی انتخاب نشده، پس از 1.1.1.1 استفاده میشه.")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
