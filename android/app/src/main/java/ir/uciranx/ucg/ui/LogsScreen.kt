package ir.uciranx.ucg.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val logs by StateBus.logs.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val list = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) list.scrollToItem(logs.size - 1)
    }

    Page("لاگ", onBack, actions = {
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(logs.joinToString("\n")))
            Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
        }) { Text("کپی") }
        TextButton(onClick = { StateBus.clear() }) { Text("پاک کردن") }
    }) { pad ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp),
                state = list,
            ) {
                items(logs.size) { i ->
                    Text(
                        logs[i],
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
