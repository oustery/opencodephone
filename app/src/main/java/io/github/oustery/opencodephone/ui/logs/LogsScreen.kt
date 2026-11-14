package io.github.oustery.opencodephone.ui.logs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.launch

/**
 * Hidden diagnostics screen (drawer → tap the version footer 5 times).
 * Tails the in-memory [AppLog] buffer live so a user can read or share what
 * actually happened — git commands with exit codes, bootstrap phases, server
 * errors — instead of a truncated message bar. A whole-log copy lives at the
 * bottom (thumb-reachable, with confirmation); tapping an entry copies just
 * that line.
 */
@Composable
fun LogsScreen(graph: io.github.oustery.opencodephone.AppGraph) {
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var minLevel by remember { mutableStateOf(AppLog.Level.DEBUG) }
    var query by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    val atBottom by remember { derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        last == null || last.index >= listState.layoutInfo.totalItemsCount - 2
    } }
    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty() && atBottom) {
            listState.scrollToItem(filtered.size - 1)
        }
    }

    // Whole-log export (chronological plain text). Depends only on the buffer
    // itself, not on the filter state — recomputes when new entries arrive.
    val export = remember(entries) { AppLog.exportText() }
    // Resolve strings in composition: stringResource() is configuration-aware,
    // while context.getString() in a composable trips lint's
    // LocalContextGetResourceValueCall and can go stale on config changes.
    val shareSubject = stringResource(R.string.logs_share_subject)
    val shareLabel = stringResource(R.string.logs_share)
    val copiedMessage = stringResource(R.string.logs_copied)
    val entryCopiedMessage = stringResource(R.string.logs_entry_copied)

    val copyWholeLog = {
        clipboard.setText(AnnotatedString(export))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }
    val copyEntry: (AppLog.Entry) -> Unit = { entry ->
        clipboard.setText(AnnotatedString(entry.exportLine()))
        Toast.makeText(context, entryCopiedMessage, Toast.LENGTH_SHORT).show()
    }

    ScreenHeader(
        title = stringResource(R.string.logs_title),
        actions = {
            IconButton(onClick = {
                // Share as plain text through the system sheet (email, chat,
                // editor — whatever the user prefers for bug reports).
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, export.take(90_000))
                    putExtra(android.content.Intent.EXTRA_SUBJECT, shareSubject)
                }
                context.startActivity(
                    android.content.Intent.createChooser(intent, shareLabel),
                )
            }) {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.logs_share),
                )
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
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
                        label = {
                            // Selected chip tints the label with the level
                            // color so the active floor reads at a glance.
                            Text(
                                stringResource(logsLevelRes(level)),
                                color = if (minLevel == level) levelColor(level) else LocalContentColor.current,
                            )
                        },
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
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.logs_search_clear),
                                )
                            }
                        }
                    } else null,
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
                    // The filter can hide a non-empty buffer — copying the
                    // whole log stays available in that state.
                    actionText = if (entries.isNotEmpty()) stringResource(R.string.logs_copy) else null,
                    onAction = if (entries.isNotEmpty()) copyWholeLog else null,
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { "${it.timestampMs}-${it.hashCode()}" }) { entry ->
                            LogRow(entry, onClick = { copyEntry(entry) })
                        }
                    }
                    if (!atBottom) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 12.dp),
                        ) {
                            IconButton(onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(filtered.size - 1)
                                }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_chevron_down),
                                    contentDescription = stringResource(R.string.logs_jump_bottom),
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = copyWholeLog,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.logs_copy))
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
            icon = painterResource(R.drawable.ic_delete),
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
private fun levelColor(level: AppLog.Level): Color = when (level) {
    AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
    AppLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
    AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
}

/** Same line format [AppLog.exportText] uses, so pasted rows align. */
private fun AppLog.Entry.exportLine(): String {
    val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
        .format(java.util.Date(timestampMs))
    val suffix = error?.let { " — $it" }.orEmpty()
    return "$time ${level.name.first()}/$tag: $message$suffix"
}

@Composable
private fun LogRow(entry: AppLog.Entry, onClick: () -> Unit) {
    val time = remember(entry.timestampMs) {
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(entry.timestampMs))
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Collapsed consecutive duplicates (AppLog dedupe) show
                    // how many identical records this line stands for.
                    if (entry.repeat > 1) "$time ×${entry.repeat}" else time,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = AppFonts.Code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = AppFonts.Code,
                    color = levelColor(entry.level),
                )
                Text(
                    entry.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = AppFonts.Code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = AppFonts.Code,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            entry.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = AppFonts.Code,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
