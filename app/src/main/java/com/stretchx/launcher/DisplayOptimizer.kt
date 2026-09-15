package com.stretchx.launcher

import android.util.Log

object DisplayOptimizer {
    private const val TAG = "DisplayOptimizer"

    data class ResolutionPreset(
        val portraitWidth: Int,
        val portraitHeight: Int,
        val density: Int,
        val label: String
    )

    // Preset configurations for Samsung Galaxy S25 Ultra (Physical: 1440x3120 Portrait / 3120x1440 Landscape)
    val PRESET_4_3_ULTRA = ResolutionPreset(1440, 1920, 440, "4:3 Ultra Netlik (1440x1920)")
    val PRESET_4_3_FPS = ResolutionPreset(1080, 1440, 360, "4:3 Yüksek FPS (1080x1440)")
    val PRESET_16_10 = ResolutionPreset(1440, 2304, 480, "16:10 Geniş Tablet (1440x2304)")

    /**
     * Applies the true stretched resolution without black bars.
     * Sequence:
     * 1. Letterbox API override (kills black borders/pillarboxing)
     * 2. Force resizable activity flags in WindowManager
     * 3. Apply custom Portrait dimensions (so Android rotates cleanly to landscape)
     * 4. Synchronize DPI to maintain touch mapping and UI scale
     */
    fun applyTrueStretch(preset: ResolutionPreset): Boolean {
        // Backup current active screen parameters before applying any change
        val preSize = ShizukuManager.exec("wm size")
        val preDensity = ShizukuManager.exec("wm density")
        Log.i(TAG, "Pre-stretch baseline: $preSize | $preDensity")
        Log.i(TAG, "Applying True Stretch: ${preset.portraitWidth}x${preset.portraitHeight} @ ${preset.density} DPI")
        // 1. Android WindowManager Letterbox Style Override
        ShizukuManager.exec("cmd window set-letterbox-style --aspectRatio 1.33")
        ShizukuManager.exec("cmd window set-letterbox-style --cornerRadius 0")
        ShizukuManager.exec("cmd window set-letterbox-style --isLetterboxActivityCornersRounded false")
        ShizukuManager.exec("cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000")

        // 2. WindowManager Scaling & Freeform Settings
        // FORCE_SCALING_MODE_DISABLED (scaling off) stops LogicalDisplay from forcing pillarbox margins
        ShizukuManager.exec("cmd window scaling off")
        ShizukuManager.exec("settings put global enable_freeform_support 1")
        ShizukuManager.exec("settings put secure force_resizable_activities 1")
        // 3. Override Display Metrics in PORTRAIT
        val sizeResult = ShizukuManager.exec("wm size ${preset.portraitWidth}x${preset.portraitHeight}")
        val densityResult = ShizukuManager.exec("wm density ${preset.density}")

        Log.i(TAG, "wm size: $sizeResult | wm density: $densityResult")
        return !sizeResult.startsWith("ERR") && !densityResult.startsWith("ERR")
    }

    /**
     * Restores S25 Ultra to native factory resolution (1440x3120, 120Hz).
     */
    fun resetToNative(): Boolean {
        Log.i(TAG, "Resetting display to native S25 Ultra parameters")
        val r1 = ShizukuManager.exec("wm size reset")
        val r2 = ShizukuManager.exec("wm density reset")
        val r3 = ShizukuManager.exec("cmd window reset-letterbox-style")
        val r4 = ShizukuManager.exec("cmd window scaling auto")

        // Ensure 120Hz LTPO refresh rate is restored
        ShizukuManager.exec("settings put system min_refresh_rate 120.0")

        return !r1.startsWith("ERR") && !r2.startsWith("ERR")
    }

    /**
     * Launches the game reliably by resolving its component or using Android's monkey launcher.
     */
    fun launchGame(packageName: String, componentName: String? = null): Boolean {
        val cmd = if (!componentName.isNullOrEmpty()) {
            "am start --windowingMode 1 -n $componentName"
        } else {
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        }
        val launchResult = ShizukuManager.exec(cmd)
        Log.i(TAG, "Launch command [$cmd] result: $launchResult")
        return !launchResult.startsWith("ERR")
    }
}
