package io.github.oustery.opencodephone.ui.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oustery.opencodephone.AppGraph
import io.github.oustery.opencodephone.R
import io.github.oustery.opencodephone.ScreenBody
import io.github.oustery.opencodephone.ScreenHeader
import io.github.oustery.opencodephone.core.designsystem.AppFonts
import io.github.oustery.opencodephone.core.designsystem.OcConfirmDialog
import io.github.oustery.opencodephone.core.designsystem.OcDialogTone
import io.github.oustery.opencodephone.core.designsystem.OcEmptyState
import io.github.oustery.opencodephone.core.model.AppLog

/**
 * Hidden diagnostics screen (drawer → tap the version footer 5 times).
 * Tails the in-memory [AppLog] buffer live so a user can read or share what
 * actually happened — git commands with exit codes, bootstrap phases, server
 * errors — instead of a truncated message bar.
 */
@Composable
fun LogsScreen(graph: io.github.oustery.opencodephone.AppGraph) {
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var minLevel by remember { mutableStateOf(AppLog.Level.DEBUG) }
    var query by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    val filtered = remember(entries, minLevel, query) {
        entries.filter { entry ->
            entry.level >= minLevel &&
                (query.isBlank() ||
                    entry.message.contains(query, ignoreCase = true) ||
                    entry.tag.contains(query, ignoreCase = true) ||
                    entry.error?.contains(query, ignoreCase = true) == true)
        }
    }
    val atBottom by derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
    }
    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty() && atBottom) {
            listState.scrollToItem(filtered.size - 1)
        }
    }

    val export = remember(filtered) { AppLog.exportText() }
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenHeader(
        title = stringResource(R.string.logs_title),
        actions = {
            IconButton(onClick = { clipboard.setText(AnnotatedString(export)) }) {
                Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.logs_copy),
                )
            }
            IconButton(onClick = {
                // Share as plain text through the system sheet (email, chat,
                // editor — whatever the user prefers for bug reports).
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, export.take(90_000))
                    putExtra(
                        android.content.Intent.EXTRA_SUBJECT,
                        context.getString(R.string.logs_share_subject),
                    )
                }
                context.startActivity(
                    android.content.Intent.createChooser(intent, context.getString(R.string.logs_share)),
                )
            }) {
                Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.logs_share),
                )
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.logs_clear),
                )
            }
        },
    )
    ScreenBody {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            // Filters: minimum level chips, substring search, tail toggle.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLog.Level.entries.forEach { level ->
                    FilterChip(
                        selected = minLevel == level,
                        onClick = { minLevel = level },
                        label = { Text(stringResource(logsLevelRes(level))) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.logs_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                )
                Text(
                    stringResource(R.string.logs_auto_scroll),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
            }
            Text(
                stringResource(R.string.logs_entries, filtered.size, entries.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (filtered.isEmpty()) {
                OcEmptyState(
                    title = stringResource(R.string.logs_empty),
                    description = stringResource(R.string.logs_empty_hint),
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { "${it.timestampMs}-${it.hashCode()}" }) { entry ->
                        LogRow(entry)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        OcConfirmDialog(
            title = stringResource(R.string.logs_clear),
            text = stringResource(R.string.logs_clear_confirm),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            icon = androidx.compose.ui.res.painterResource(R.drawable.ic_delete),
            tone = OcDialogTone.DESTRUCTIVE,
            onConfirm = {
                AppLog.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

private fun logsLevelRes(level: AppLog.Level): Int = when (level) {
    AppLog.Level.DEBUG -> R.string.logs_level_debug
    AppLog.Level.INFO -> R.string.logs_level_info
    AppLog.Level.WARN -> R.string.logs_level_warn
    AppLog.Level.ERROR -> R.string.logs_level_error
}

@Composable
private fun LogRow(entry: AppLog.Entry) {
    val levelColor = when (entry.level) {
        AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
        AppLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
        AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    val time = remember(entry.timestampMs) {
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestampMs))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = AppFonts.Code,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.level.name.first().toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = AppFonts.Code,
            color = levelColor,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${entry.tag}: ${entry.message}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = AppFonts.Code,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            entry.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = AppFonts.Code,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
