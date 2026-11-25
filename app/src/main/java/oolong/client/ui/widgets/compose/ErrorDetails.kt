package oolong.client.ui.widgets.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.components.ErrorRecords
import oolong.client.toastShort
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.utils.TiebaUtil

/**
 * Under an error the app can't explain, a quiet 「详情」 once the error is kept in [ErrorRecords]:
 * it shows what is known of it, to copy or send to the developer.
 */
@Composable
internal fun ErrorDetailsButton(error: Throwable) {
    val context = LocalContext.current
    var id by remember(error) { mutableStateOf<String?>(null) }
    LaunchedEffect(error) {
        id = withContext(Dispatchers.IO) { runCatching { ErrorRecords.record(context, error) }.getOrNull() }
    }
    val recordId = id ?: return
    val dialogState = rememberDialogState()
    TextButton(onClick = { dialogState.show() }, color = ExtendedTheme.colors.textSecondary) {
        Text(text = stringResource(id = R.string.button_error_details))
    }
    ErrorDetailsDialog(id = recordId, dialogState = dialogState)
}

/** The record [id] in short, with its full text to copy and, for a release build's, to send. */
@Composable
fun ErrorDetailsDialog(id: String, dialogState: DialogState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var record by remember(id) { mutableStateOf<String?>(null) }
    var sendable by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, dialogState.show) {
        if (!dialogState.show) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            record = ErrorRecords.read(context, id)
            sendable = ErrorRecords.canSend(context, id) && !ErrorRecords.isSent(context, id)
        }
    }
    val copy = { record?.let { TiebaUtil.copyText(context, it) } }
    Dialog(
        dialogState = dialogState,
        title = { Text(text = stringResource(id = R.string.title_error_details)) },
        buttons = {
            if (sendable) {
                DialogPositiveButton(
                    text = stringResource(id = R.string.title_send_crash_log),
                    onClick = {
                        scope.launch {
                            val sent = withContext(Dispatchers.IO) { ErrorRecords.send(context, id) }
                            context.toastShort(if (sent) R.string.toast_crash_log_sent else R.string.toast_error_send_failed)
                        }
                    },
                )
                DialogNegativeButton(text = stringResource(id = R.string.title_copy), onClick = { copy() })
            } else {
                DialogPositiveButton(text = stringResource(id = R.string.title_copy), onClick = { copy() })
                DialogNegativeButton(text = stringResource(id = R.string.btn_close))
            }
        },
    ) {
        val text = record ?: return@Dialog
        val header = text.substringBefore("\n\n").lines()
        Column(
            modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // What it came from, then which build and when, as the record has them
            Text(text = header.lastOrNull().orEmpty(), style = TextStyles.body, color = ExtendedTheme.colors.text)
            Text(
                text = header.dropLast(1).filterNot { it.startsWith("System: ") }.joinToString("\n"),
                style = TextStyles.footnote.copy(fontFamily = FontFamily.Monospace),
                color = ExtendedTheme.colors.textSecondary,
            )
        }
    }
}
