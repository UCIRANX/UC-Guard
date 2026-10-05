package ir.uciranx.ucg.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciranx.ucg.core.StateBus

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val live by StateBus.logs.collectAsStateWithLifecycle()
    val previous = remember { StateBus.previousLog() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shown = if (tab == 0) live else previous

    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val list = rememberLazyListState()

    LaunchedEffect(tab, shown.size) {
        if (shown.isNotEmpty()) list.scrollToItem(shown.size - 1)
    }

    Page("لاگ", onBack, actions = {
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(shown.joinToString("\n")))
            Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
        }) { Text("کپی") }
        if (tab == 0) TextButton(onClick = { StateBus.clear() }) { Text("پاک کردن") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("این دفعه") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("دفعه قبل (بعد از کرش)") })
            }
            if (shown.isEmpty()) {
                Hint(if (tab == 0) "هنوز لاگی نیست." else "از دفعه قبل لاگی نمونده.")
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    state = list,
                ) {
                    items(shown.size) { i ->
                        val line = shown[i]
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = when {
                                line.contains("CRASH") || line.contains("error", true) -> Color(0xFFFF8A80)
                                line.contains("WARN") -> Color(0xFFFFD180)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
