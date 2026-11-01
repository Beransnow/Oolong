package com.huanchengfly.tieba.post.ui.page.picker

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseComposeActivity
import com.huanchengfly.tieba.post.toastShort
import dagger.hilt.android.AndroidEntryPoint

/**
 * Picks pictures from the device without the system photo picker. Returns them as the result's
 * clip data, like the photo picker does.
 */
@AndroidEntryPoint
class ImagePickerActivity : BaseComposeActivity() {
    private val viewModel: ImagePickerViewModel by viewModels()

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        var access by remember { mutableStateOf(currentAccess()) }
        var asked by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            access = currentAccess()
            if (access == PickerAccess.Denied && !shouldShowRequestPermissionRationale(readPermission)) {
                access = PickerAccess.DeniedForever
            }
        }
        // Coming back from the permission dialog or the settings may have changed what is readable
        LifecycleResumeEffect(Unit) {
            val now = currentAccess()
            if (now != PickerAccess.Denied || access != PickerAccess.DeniedForever) access = now
            if (now != PickerAccess.Denied) viewModel.load()
            onPauseOrDispose {}
        }
        LaunchedEffect(Unit) {
            if (access == PickerAccess.Denied && !asked) {
                asked = true
                permissionLauncher.launch(requestedPermissions)
            }
        }
        LaunchedEffect(Unit) {
            viewModel.limitReached.collect { toastShort(R.string.toast_picker_limit, it) }
        }

        ImagePickerScreen(
            state = state,
            access = access,
            onBack = { finish() },
            onShowAlbum = viewModel::showAlbum,
            onToggle = viewModel::toggle,
            onDone = ::finishWith,
            onRequestAccess = { permissionLauncher.launch(requestedPermissions) },
            onOpenSettings = {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            },
        )
    }

    private fun currentAccess(): PickerAccess = when {
        isGranted(readPermission) -> PickerAccess.All
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PickerAccess.Selected

        else -> PickerAccess.Denied
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun finishWith(uris: List<Uri>) {
        val result = Intent()
        if (uris.isNotEmpty()) {
            result.clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        private val readPermission: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        private val requestedPermissions: Array<String>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            } else {
                arrayOf(readPermission)
            }

        fun intent(context: Context, maxItems: Int): Intent =
            Intent(context, ImagePickerActivity::class.java).putExtra(ImagePickerViewModel.EXTRA_MAX_ITEMS, maxItems)
    }
}
