package com.huanchengfly.tieba.post.utils

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.snackbar.Snackbar
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import com.huanchengfly.tieba.post.components.images.loadBitmap
import com.huanchengfly.tieba.post.dpToPxFloat
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.ui.common.theme.utils.ColorStateListUtils
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.WebViewKey
import com.huanchengfly.tieba.post.utils.Util.createSnackbar

@JvmOverloads
fun getItemBackgroundDrawable(
    context: Context,
    position: Int,
    itemCount: Int,
    positionOffset: Int = 0,
    radius: Float = 8f.dpToPxFloat(),
    colors: IntArray = intArrayOf(R.color.default_color_card),
    ripple: Boolean = true
): Drawable {
    val realPos = position + positionOffset
    val maxPos = itemCount - 1 + positionOffset
    val shape = GradientDrawable().apply {
        color = ColorStateListUtils.createColorStateList(context, colors[realPos % colors.size])
        if (realPos == 0 && realPos == maxPos) {
            cornerRadius = radius
        } else if (realPos == 0) {
            cornerRadii = floatArrayOf(
                radius,
                radius,
                radius,
                radius,
                0f, 0f, 0f, 0f
            )
        } else if (realPos == maxPos) {
            cornerRadii = floatArrayOf(
                0f, 0f, 0f, 0f,
                radius,
                radius,
                radius,
                radius
            )
        } else {
            cornerRadius = 0f
        }
    }
    return if (ripple) {
        wrapRipple(
            Util.getColorByAttr(context, R.attr.colorControlHighlight, R.color.transparent),
            shape
        )
    } else {
        shape
    }
}

fun getRadiusDrawable(
    topLeftPx: Float = 0f,
    topRightPx: Float = 0f,
    bottomLeftPx: Float = 0f,
    bottomRightPx: Float = 0f,
    ripple: Boolean = false
): Drawable {
    val drawable = GradientDrawable().apply {
        color = ColorStateList.valueOf(Color.WHITE)
        cornerRadii = floatArrayOf(
            topLeftPx, topLeftPx,
            topRightPx, topRightPx,
            bottomRightPx, bottomRightPx,
            bottomLeftPx, bottomLeftPx
        )
    }
    return if (ripple)
        wrapRipple(
            Util.getColorByAttr(
                App.INSTANCE,
                R.attr.colorControlHighlight,
                R.color.transparent
            ), drawable
        )
    else drawable
}

fun getRadiusDrawable(
    radiusPx: Float = 0f,
    ripple: Boolean = false
): Drawable {
    return getRadiusDrawable(radiusPx, radiusPx, radiusPx, radiusPx, ripple)
}

fun wrapRipple(rippleColor: Int, drawable: Drawable): Drawable {
    return RippleDrawable(ColorStateList.valueOf(rippleColor), drawable, drawable)
}


@JvmOverloads
fun getIntermixedColorBackground(
    context: Context,
    position: Int,
    itemCount: Int,
    positionOffset: Int = 0,
    radius: Float = 8f.dpToPxFloat(),
    colors: IntArray = intArrayOf(R.color.default_color_card),
    ripple: Boolean = true
): Drawable {
    return getItemBackgroundDrawable(
        context,
        position,
        itemCount,
        positionOffset,
        radius,
        if (context.appPreferences.listItemsBackgroundIntermixed) {
            colors
        } else {
            intArrayOf(colors[0])
        },
        ripple
    )
}

fun launchUrl(
    context: Context,
    navigator: Navigator,
    url: String,
) {
    val uri = Uri.parse(url)
    val host = uri.host
    val path = uri.path
    val scheme = uri.scheme
    if (host == null || scheme == null || path == null) {
        return
    }
    if (scheme == "tiebaclient") {
        when (uri.getQueryParameter("action")) {
            "preview_file" -> {
                val realUrl = uri.getQueryParameter("url")
                if (realUrl.isNullOrEmpty()) {
                    return
                }
                launchUrl(context, navigator, realUrl)
            }

            else -> {
                context.toastShort(R.string.toast_feature_unavailable)
            }
        }
        return
    }
    if (!path.contains("android_asset")) {
        if (path == "/mo/q/checkurl") {
            launchUrl(
                context,
                navigator,
                uri.getQueryParameter("url")?.replace("http://https://", "https://").orEmpty()
            )
            return
        }
        if (host == "tieba.baidu.com" && path.startsWith("/p/")) {
            val threadId = path.substring(3).toLongOrNull()
            if (threadId != null) {
                navigator.navigate(
                    ThreadKey(threadId)
                )
            }
            return
        }
        val isTiebaLink =
            host.contains("tieba.baidu.com") || host.contains("wappass.baidu.com") || host.contains(
                "ufosdk.baidu.com"
            ) || host.contains("m.help.baidu.com")
        if (isTiebaLink || context.appPreferences.useWebView) {
            navigator.navigate(
                WebViewKey(url)
            )
        } else {
            if (context.appPreferences.useCustomTabs) {
                val intentBuilder = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(
                                ThemeUtils.getColorByAttr(
                                    context,
                                    R.attr.colorToolbar
                                )
                            )
                            .build()
                    )
                try {
                    intentBuilder.build().launchUrl(context, uri)
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
}

fun showErrorSnackBar(view: View, throwable: Throwable) {
    val code = if (throwable is TiebaException) throwable.code else -1
    createSnackbar(
        view,
        view.context.getString(R.string.snackbar_error, code),
        Snackbar.LENGTH_LONG
    )
        .setAction(R.string.button_detail) {
            val stackTrace = throwable.stackTraceToString()
            DialogUtil.build(view.context)
                .setTitle(R.string.title_dialog_error_detail)
                .setMessage(stackTrace)
                .setPositiveButton(R.string.button_copy_detail) { _, _ ->
                    TiebaUtil.copyText(view.context, stackTrace)
                }
                .setNegativeButton(R.string.btn_close, null)
                .show()
        }
        .show()
}

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

@SuppressLint("BatteryLife")
fun Context.requestIgnoreBatteryOptimizations() {
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${packageName}")
        startActivity(intent)
    }
}

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    powerManager.isIgnoringBatteryOptimizations(packageName)

suspend fun requestPinShortcut(
    context: Context,
    shortcutId: String,
    iconImageUri: String,
    label: String,
    shortcutIntent: Intent,
    onSuccess: () -> Unit = {},
    onFailure: (String) -> Unit = {}
) {
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val icon = context.loadBitmap(iconImageUri)
        if (icon != null) {
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(shortcutIntent)
                .setShortLabel(label)
                .build()
            val result = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            if (result) onSuccess() else onFailure(context.getString(R.string.launcher_not_support_pin_shortcut))
        } else {
            onFailure(context.getString(R.string.load_shortcut_icon_fail))
        }
    } else {
        onFailure(context.getString(R.string.launcher_not_support_pin_shortcut))
    }
}