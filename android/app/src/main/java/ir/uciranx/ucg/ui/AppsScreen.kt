package ir.uciranx.ucg.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciranx.ucg.data.SettingsStore
import ir.uciranx.ucg.data.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String, val system: Boolean)

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(300)
}

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val me = ctx.packageName
    @Suppress("DEPRECATION")
    val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return list.asSequence()
        .filter { it.packageName != me }
        .map { ai ->
            AppInfo(
                pkg = ai.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                system = pm.getLaunchIntentForPackage(ai.packageName) == null,
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by SettingsStore.flow.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }

    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    Page("تونل‌سازی برنامه‌ها", onBack) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            SplitMode.entries.forEach { m ->
                RadioRow(m.title, m.hint, s.splitMode == m) {
                    SettingsStore.update { it.copy(splitMode = m) }
                }
            }
            Hint("تغییرات از اتصال بعدی اعمال میشن.")

            if (s.splitMode == SplitMode.OFF) return@Column

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("جستجوی برنامه") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                Text("نمایش برنامه‌های سیستمی", Modifier.weight(1f))
                Text(
                    "${s.selectedApps.size} انتخاب",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            val all = apps
            if (all == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Selected apps float to the top once, when the list is built, not on every tap.
            val firstSelection = remember(all) { s.selectedApps }
            val shown = remember(all, query, showSystem) {
                all.filter { a ->
                    (showSystem || !a.system || a.pkg in firstSelection) &&
                        (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
                }.sortedByDescending { it.pkg in firstSelection }
            }

            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in s.selectedApps
                    val toggle = { on: Boolean ->
                        SettingsStore.update {
                            it.copy(selectedApps = if (on) it.selectedApps + app.pkg else it.selectedApps - app.pkg)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(!checked) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = { toggle(it) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(IconCache.cache.get(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
                        .also { IconCache.cache.put(pkg, it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Box(Modifier.size(40.dp)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
