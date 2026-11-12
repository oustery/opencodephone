package io.github.oustery.opencodephone.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Shared dialog voice for the whole app (modal-dialogs pass): every
 * confirmation carries a tinted leading emblem, one clear primary action and
 * one quiet dismissal, and destructive actions are rendered in the error
 * color so they can never be confused with safe ones.
 */

/** Visual voice of a dialog: calm, destructive, or accent (multi-step flows). */
enum class OcDialogTone { NEUTRAL, DESTRUCTIVE, ACCENT }

/** Tinted circular emblem shown above the dialog title. */
@Composable
private fun OcDialogEmblem(icon: Painter, tone: OcDialogTone) {
    val container = when (tone) {
        OcDialogTone.NEUTRAL -> MaterialTheme.colorScheme.primaryContainer
        OcDialogTone.DESTRUCTIVE -> MaterialTheme.colorScheme.errorContainer
        OcDialogTone.ACCENT -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (tone) {
        OcDialogTone.NEUTRAL -> MaterialTheme.colorScheme.onPrimaryContainer
        OcDialogTone.DESTRUCTIVE -> MaterialTheme.colorScheme.onErrorContainer
        OcDialogTone.ACCENT -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The standard confirmation dialog. Replaces the ad-hoc AlertDialogs whose
 * button hierarchy drifted between screens.
 */
@Composable
fun OcConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    tone: OcDialogTone = OcDialogTone.NEUTRAL,
    dismissLabel: String? = null,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { OcDialogEmblem(it, tone) } },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (tone == OcDialogTone.DESTRUCTIVE) {
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(confirmLabel) }
            } else {
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        },
        dismissButton = dismissLabel?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        },
    )
}

/**
 * Standard single-field input dialog: autofocused field, IME-friendly
 * (Done/Go confirms), optional inline validation shown as supporting text
 * with a disabled confirm button.
 */
@Composable
fun OcInputDialog(
    title: String,
    valueLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    confirmLabel: String,
    dismissLabel: String,
    /** Returns an error message for the current value, or null when valid. */
    validate: ((String) -> String?)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    val error = validate?.invoke(value.text)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val valid = value.text.isNotBlank() && error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.imePadding(),
        icon = icon?.let { { OcDialogEmblem(it, OcDialogTone.NEUTRAL) } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(valueLabel) },
                    placeholder = placeholder?.let { { Text(it) } },
                    supportingText = error?.let { reason -> { Text(reason) } }
                        ?: supportingText?.let { { Text(it) } },
                    isError = error != null,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    // The IME action confirms — the dialog never traps the
                    // keyboard behind an extra tap.
                    keyboardActions = KeyboardActions(
                        onDone = { if (valid) onConfirm(value.text.trim()) },
                        onGo = { if (valid) onConfirm(value.text.trim()) },
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value.text.trim()) },
                enabled = valid,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * Two-line helper for dialog bodies that pair a headline sentence with a
 * quieter supporting paragraph.
 */
@Composable
fun OcDialogBody(headline: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(headline, style = MaterialTheme.typography.bodyMedium)
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
