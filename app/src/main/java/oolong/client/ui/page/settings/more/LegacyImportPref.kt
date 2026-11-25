package oolong.client.ui.page.settings.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.legacy.LegacyImport
import oolong.client.ui.common.prefs.widgets.TextPref
import oolong.client.util.findActivity
import oolong.client.util.toastShort

/**
 * Brings the old app's data over: asks its exporter, which asks the person, then starts the app
 * again with the data in place. An old app too old to export says what to install first.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LegacyImportPref(source: LegacyImport.Source) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(id = R.string.toast_import_legacy_failed)
    val export = rememberLauncherForActivityResult(LegacyImport.Export) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (LegacyImport.receive(context, uri)) {
                context.findActivity()?.let(LegacyImport::restart)
            } else {
                context.toastShort(failed)
            }
        }
    }
    val ready = source == LegacyImport.Source.Ready
    TextPref(
        title = stringResource(id = R.string.title_import_legacy),
        summary = if (ready) null else stringResource(id = R.string.tip_import_legacy_update),
        enabled = ready,
        onClick = { export.launch(Unit) },
    )
}
