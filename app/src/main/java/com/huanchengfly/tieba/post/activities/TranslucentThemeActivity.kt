package com.huanchengfly.tieba.post.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.ColorInt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.gyf.immersionbar.ImmersionBar
import com.huanchengfly.tieba.post.*
import com.huanchengfly.tieba.post.App.Companion.translucentBackground
import com.huanchengfly.tieba.post.adapters.TranslucentThemeColorAdapter
import com.huanchengfly.tieba.post.adapters.WallpaperAdapter
import com.huanchengfly.tieba.post.api.LiteApi
import com.huanchengfly.tieba.post.api.retrofit.doIfSuccess
import com.huanchengfly.tieba.post.components.MyLinearLayoutManager
import com.huanchengfly.tieba.post.components.dividers.HorizontalSpacesDecoration
import com.huanchengfly.tieba.post.components.images.BlurTransformation
import com.huanchengfly.tieba.post.components.images.loadBitmap
import com.huanchengfly.tieba.post.components.images.loadDrawable
import com.huanchengfly.tieba.post.interfaces.OnItemClickListener
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.ui.widgets.theme.TintMaterialButton
import com.huanchengfly.tieba.post.utils.*
import com.huanchengfly.tieba.post.utils.ThemeUtil.TRANSLUCENT_THEME_DARK
import com.huanchengfly.tieba.post.utils.ThemeUtil.TRANSLUCENT_THEME_LIGHT
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

class TranslucentThemeActivity : BaseActivity(), View.OnClickListener, OnSeekBarChangeListener,
    ColorPickerDialogListener {
    private var mUri: Uri? = null
    private var alpha = 0
    private var blur = 0
    private var mPalette: Palette? = null

    val mSelectColor: View by lazy { findViewById(R.id.select_color) }

    val recommendWallpapers: View by lazy { findViewById(R.id.recommend_wallpapers) }

    val recommendWallpapersRv: RecyclerView by lazy { findViewById(R.id.wallpapers_rv) }

    val mProgress: View by lazy { findViewById(R.id.progress) }

    val darkColorBtn: TintMaterialButton by lazy { findViewById(R.id.dark_color) }

    val lightColorBtn: TintMaterialButton by lazy { findViewById(R.id.light_color) }

    val backBtn: View by lazy { findViewById(R.id.button_back) }

    val bottomSheet: LinearLayout by lazy { findViewById(R.id.bottom_sheet) }

    val finishBtn: View by lazy { findViewById(R.id.button_finish) }

    val maskView: View by lazy { findViewById(R.id.mask) }

    val experimentalTipView: View by lazy { findViewById(R.id.experimental_tip) }

    val colorTheme: ViewGroup by lazy { findViewById(R.id.color_theme) }

    private val selectImageLauncher = registerPickMediasLauncher { (_, uris) ->
        if (uris.isNotEmpty()) {
            val sourceUri = uris[0]
            launchUCrop(sourceUri)
        }
    }

    var wallpapers: List<String>? = null
        set(value) {
            field = value
            refreshWallpapers()
        }
    private val wallpaperAdapter: WallpaperAdapter by lazy { WallpaperAdapter(this) }

    private val mTranslucentThemeColorAdapter: TranslucentThemeColorAdapter by lazy {
        TranslucentThemeColorAdapter(
            this
        )
    }

    /** The picked background at screen size, blurred as chosen, in a bitmap whose colours can be read. */
    private suspend fun loadBackground(): Drawable? = loadDrawable(mUri) {
        size(App.ScreenInfo.EXACT_SCREEN_WIDTH, App.ScreenInfo.EXACT_SCREEN_HEIGHT)
        scale(Scale.FILL)
        allowHardware(false)
        if (blur > 0) {
            transformations(BlurTransformation(this@TranslucentThemeActivity, blur))
        }
    }

    private fun launchUCrop(sourceUri: Uri) {
        mProgress.visibility = View.VISIBLE
        launch {
            val bitmap = loadBitmap(sourceUri) {
                size(App.ScreenInfo.EXACT_SCREEN_WIDTH * 2, App.ScreenInfo.EXACT_SCREEN_HEIGHT * 2)
                precision(Precision.INEXACT)
            }
            if (bitmap != null) {
                mProgress.visibility = View.GONE
                val file =
                    ImageUtil.bitmapToFile(bitmap, File(cacheDir, "origin_background.jpg"))
                val sourceFileUri = Uri.fromFile(file)
                val destUri = Uri.fromFile(File(filesDir, "cropped_background.jpg"))
                val height = App.ScreenInfo.EXACT_SCREEN_HEIGHT.toFloat()
                val width = App.ScreenInfo.EXACT_SCREEN_WIDTH.toFloat()
                UCrop.of(sourceFileUri, destUri)
                    .withAspectRatio(width / height, 1f)
                    .withOptions(UCrop.Options().apply {
                        setShowCropFrame(true)
                        setShowCropGrid(true)
                        setToolbarColor(
                            ThemeUtils.getColorByAttr(
                                this@TranslucentThemeActivity,
                                R.attr.colorPrimary
                            )
                        )
                        setToolbarWidgetColor(
                            ThemeUtils.getColorByAttr(
                                this@TranslucentThemeActivity,
                                R.attr.colorTextOnPrimary
                            )
                        )
                        setActiveControlsWidgetColor(
                            ThemeUtils.getColorByAttr(
                                this@TranslucentThemeActivity,
                                R.attr.colorAccent
                            )
                        )
                        setLogoColor(
                            ThemeUtils.getColorByAttr(
                                this@TranslucentThemeActivity,
                                R.attr.colorPrimary
                            )
                        )
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                    })
                    .start(this@TranslucentThemeActivity)
            } else {
                mProgress.visibility = View.GONE
                toastShort(R.string.text_load_failed)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            mUri = UCrop.getOutput(data!!)
            invalidateFinishBtn()
            refreshBackground()
        } else if (resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(data!!)
            cropError!!.printStackTrace()
        }
    }

    private fun refreshWallpapers() {
        if (wallpapers.isNullOrEmpty()) {
            recommendWallpapers.visibility = View.GONE
        } else {
            recommendWallpapers.visibility = View.VISIBLE
            wallpaperAdapter.setData(wallpapers)
        }
    }

    private fun refreshBackground() {
        mProgress.visibility = View.VISIBLE
        if (mUri == null) {
            findViewById<View>(R.id.background).setBackgroundColor(Color.BLACK)
            mProgress.visibility = View.GONE
            return
        }
        launch {
            val drawable = loadBackground()
            if (drawable != null) {
                drawable.alpha = alpha
                findViewById<View>(R.id.background).background = drawable
                mPalette = Palette.from(ImageUtil.drawableToBitmap(drawable)).generate()
                mTranslucentThemeColorAdapter.setPalette(mPalette)
                mSelectColor.visibility = View.VISIBLE
                mProgress.visibility = View.GONE
            }
        }
    }

    override fun refreshStatusBarColor() {
        ImmersionBar.with(this)
            .transparentBar()
            .init()
    }

    override fun getLayoutId(): Int {
        return R.layout.activity_translucent_theme
    }

    @SuppressLint("ApplySharedPref", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        experimentalTipView.setOnClickListener {
            showDialog {
                setTitle(R.string.title_translucent_theme_experimental_feature)
                setMessage(
                    HtmlCompat.fromHtml(
                        getString(R.string.tip_translucent_theme),
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                )
                setNegativeButton(R.string.btn_close, null)
            }
        }
        listOf(
            findViewById(R.id.custom_color),
            findViewById(R.id.select_pic),
            darkColorBtn,
            lightColorBtn,
            backBtn,
            finishBtn
        ).forEach {
            it.setOnClickListener(this@TranslucentThemeActivity)
        }
        wallpapers =
            CacheUtil.getCache(this, "recommend_wallpapers", List::class.java) as List<String>?
        colorTheme.enableChangingLayoutTransition()
        wallpaperAdapter.setOnItemClickListener { _, item, _ ->
            launchUCrop(Uri.parse(item))
        }
        recommendWallpapersRv.addItemDecoration(
            HorizontalSpacesDecoration(
                0,
                0,
                16.dpToPx(),
                16.dpToPx(),
                false
            )
        )
        recommendWallpapersRv.adapter = wallpaperAdapter
        recommendWallpapersRv.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mTranslucentThemeColorAdapter.onItemClickListener =
            OnItemClickListener { _: View?, themeColor: Int, _: Int, _: Int ->
                appPreferences.translucentPrimaryColor = toString(themeColor)
                maskView.post { ThemeUtils.refreshUI(this, this) }
            }
        (findViewById<RecyclerView>(R.id.select_color_recycler_view)).apply {
            addItemDecoration(HorizontalSpacesDecoration(0, 0, 12.dpToPx(), 12.dpToPx(), false))
            layoutManager = MyLinearLayoutManager(
                this@TranslucentThemeActivity,
                MyLinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = mTranslucentThemeColorAdapter
        }
        alpha = appPreferences.translucentBackgroundAlpha
        blur = appPreferences.translucentBackgroundBlur
        (findViewById<SeekBar>(R.id.alpha)).apply {
            progress = this@TranslucentThemeActivity.alpha
            setOnSeekBarChangeListener(this@TranslucentThemeActivity)
        }
        (findViewById<SeekBar>(R.id.blur)).apply {
            progress = this@TranslucentThemeActivity.blur
            setOnSeekBarChangeListener(this@TranslucentThemeActivity)
        }
        mProgress.setOnTouchListener { _: View?, _: MotionEvent? -> true }
        mProgress.visibility = View.GONE
        val file = File(filesDir, "cropped_background.jpg")
        if (file.exists()) {
            mUri = Uri.fromFile(file)
            invalidateFinishBtn()
        }
        val bottomSheetBehavior =
            (bottomSheet.layoutParams as CoordinatorLayout.LayoutParams).behavior as BottomSheetBehavior
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                maskView.alpha = slideOffset
                maskView.visibility = if (slideOffset < 0.01f) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }

        })
        maskView.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        refreshBackground()
        refreshTheme()
        fetchWallpapers()
    }

    private fun fetchWallpapers() {
        launch(IO + job) {
            LiteApi.instance
                .wallpapersAsync()
                .doIfSuccess {
                    CacheUtil.putCache(this@TranslucentThemeActivity, "recommend_wallpapers", it)
                    wallpapers = it
                }
        }
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        appPreferences.translucentPrimaryColor = toString(color)
        ThemeUtils.refreshUI(this, this)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.select_color -> return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun savePic(callback: SavePicCallback<File>) {
        runCatching {
            val oldFilePath = appPreferences.translucentThemeBackgroundPath
            if (oldFilePath != null) {
                val oldFile = File(oldFilePath)
                oldFile.delete()
            }
        }
        mProgress.visibility = View.VISIBLE
        launch {
            val drawable = loadBackground()
            if (drawable != null) {
                drawable.alpha = alpha
                val bitmap = ImageUtil.drawableToBitmap(drawable)
                val file = ImageUtil.compressImage(
                    bitmap,
                    File(filesDir, "background_${System.currentTimeMillis()}.jpg"),
                    maxSizeKb = 512,
                    initialQuality = 97
                )
                mPalette = Palette.from(bitmap).generate()
                appPreferences.translucentThemeBackgroundPath = file.absolutePath
                ThemeUtils.refreshUI(
                    this@TranslucentThemeActivity,
                    this@TranslucentThemeActivity
                )
                callback.onSuccess(file)
            }
        }
    }

    private fun invalidateFinishBtn() {
        if (mUri != null) {
            finishBtn.visibility = View.VISIBLE
        } else {
            finishBtn.visibility = View.GONE
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {
        when (seekBar.id) {
            R.id.alpha -> alpha = seekBar.progress
            R.id.blur -> blur = seekBar.progress
        }
        refreshBackground()
    }

    private fun refreshTheme() {
        when (appPreferences.translucentBackgroundTheme) {
            TRANSLUCENT_THEME_DARK -> {
                darkColorBtn.setBackgroundTintResId(R.color.default_color_accent)
                darkColorBtn.setTextColorResId(R.color.white)
                darkColorBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    ContextCompat.getDrawable(this, R.drawable.ic_round_check_circle),
                    null,
                    null,
                    null
                )
                lightColorBtn.setBackgroundTintResId(R.color.color_divider)
                lightColorBtn.setTextColorResId(R.color.color_text_secondary)
                lightColorBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,
                    null,
                    null,
                    null
                )
            }
            TRANSLUCENT_THEME_LIGHT -> {
                darkColorBtn.setBackgroundTintResId(R.color.color_divider)
                darkColorBtn.setTextColorResId(R.color.color_text_secondary)
                darkColorBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                lightColorBtn.setBackgroundTintResId(R.color.default_color_accent)
                lightColorBtn.setTextColorResId(R.color.white)
                lightColorBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    ContextCompat.getDrawable(this, R.drawable.ic_round_check_circle),
                    null,
                    null,
                    null
                )
            }
        }
    }

    override fun finish() {
        ImageCacheUtil.clearImageMemoryCache(this)
        super.finish()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_finish -> {
                appPreferences.apply {
                    translucentBackgroundAlpha = alpha
                    translucentBackgroundBlur = blur
                }
                savePic(object : SavePicCallback<File> {
                    override fun onSuccess(t: File) {
                        ThemeUtil.switchTheme(ThemeUtil.THEME_TRANSLUCENT, false)
                        toastShort(R.string.toast_save_pic_success)
                        translucentBackground = null
                        mProgress.visibility = View.GONE
                        finish()
                    }
                })
            }
            R.id.button_back -> {
                finish()
            }
            R.id.select_pic -> selectImageLauncher.launch(PickMediasRequest())
            R.id.custom_color -> {
                val primaryColorPicker = ColorPickerDialog.newBuilder()
                    .setDialogTitle(R.string.title_color_picker_primary)
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setShowAlphaSlider(true)
                    .setDialogId(0)
                    .setAllowPresets(false)
                    .setColor(ThemeUtils.getColorById(this, R.color.default_color_primary))
                    .create()
                primaryColorPicker.setColorPickerDialogListener(this)
                primaryColorPicker.show(
                    supportFragmentManager,
                    "ColorPicker_TranslucentThemePrimaryColor"
                )
            }
            R.id.dark_color -> {
                appPreferences.translucentBackgroundTheme = TRANSLUCENT_THEME_DARK
                refreshTheme()
            }
            R.id.light_color -> {
                appPreferences.translucentBackgroundTheme = TRANSLUCENT_THEME_LIGHT
                refreshTheme()
            }
        }
    }

    interface SavePicCallback<T> {
        fun onSuccess(t: T)
    }

    companion object {
        val TAG = TranslucentThemeActivity::class.java.simpleName
        const val REQUEST_CODE_CHOOSE = 2
        fun toString(alpha: Int, red: Int, green: Int, blue: Int): String {
            val hr = Integer.toHexString(red)
            val hg = Integer.toHexString(green)
            val hb = Integer.toHexString(blue)
            val ha = Integer.toHexString(alpha)
            return "#" + fixHexString(ha) + fixHexString(hr) + fixHexString(hg) + fixHexString(hb)
        }

        private fun fixHexString(string: String): String {
            var hexStr = string
            if (hexStr.isEmpty()) {
                hexStr = "00"
            }
            if (hexStr.length == 1) {
                hexStr = "0$hexStr"
            }
            if (hexStr.length > 2) {
                hexStr = hexStr.substring(0, 2)
            }
            return hexStr
        }

        fun toString(@ColorInt color: Int): String {
            return toString(
                Color.alpha(color),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
        }
    }
}