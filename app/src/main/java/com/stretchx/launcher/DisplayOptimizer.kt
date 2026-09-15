package com.stretchx.launcher

import android.content.Context
import android.util.Log

object DisplayOptimizer {
    private const val TAG = "DisplayOptimizer"

    const val PREFS_NAME = "stretchx_prefs"
    const val KEY_BACKUP_SIZE = "backup_size"
    const val KEY_BACKUP_DENSITY = "backup_density"
    const val KEY_HAD_OVERRIDE = "had_override"
    private const val KEY_IS_STRETCHED = "is_stretched"

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
     * Backs up the user's active display state (size, density, and whether an override was present).
     * Distinguishes between native QHD+ (1440x3120) and FHD+ (1080x2340) override modes.
     */
    fun backupCurrentDisplayState(context: Context) {
        try {
            if (!ShizukuManager.isAvailable() || !ShizukuManager.hasPermission()) {
                Log.w(TAG, "Shizuku not available or lacks permission. Skipping display backup.")
                return
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_IS_STRETCHED, false)) {
                Log.i(TAG, "Display is currently stretched; preserving existing baseline backup.")
                return
            }

            val sizeOut = ShizukuManager.exec("wm size")
            val densityOut = ShizukuManager.exec("wm density")
            if (sizeOut.startsWith("ERR") || densityOut.startsWith("ERR")) {
                Log.w(TAG, "Failed to query wm size/density via Shizuku: size=$sizeOut, density=$densityOut")
                return
            }

            val overrideSizeRegex = Regex("""Override size:\s*([0-9]+x[0-9]+)""", RegexOption.IGNORE_CASE)
            val physicalSizeRegex = Regex("""Physical size:\s*([0-9]+x[0-9]+)""", RegexOption.IGNORE_CASE)
            val overrideDensityRegex = Regex("""Override density:\s*([0-9]+)""", RegexOption.IGNORE_CASE)
            val physicalDensityRegex = Regex("""Physical density:\s*([0-9]+)""", RegexOption.IGNORE_CASE)

            val overrideSizeMatch = overrideSizeRegex.find(sizeOut)
            val physicalSizeMatch = physicalSizeRegex.find(sizeOut)
            val overrideDensityMatch = overrideDensityRegex.find(densityOut)
            val physicalDensityMatch = physicalDensityRegex.find(densityOut)

            // Detect if user had a custom resolution mode (e.g. FHD+ 1080x2340 or custom DPI)
            val hadOverride = overrideSizeMatch != null || overrideDensityMatch != null
            val backupSize = overrideSizeMatch?.groupValues?.get(1)
                ?: physicalSizeMatch?.groupValues?.get(1)
                ?: "1440x3120"
            val backupDensity = overrideDensityMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: physicalDensityMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: 560

            // If current override size matches one of the stretch presets, don't overwrite an existing valid backup
            val knownPresets = listOf(
                "${PRESET_4_3_ULTRA.portraitWidth}x${PRESET_4_3_ULTRA.portraitHeight}",
                "${PRESET_4_3_FPS.portraitWidth}x${PRESET_4_3_FPS.portraitHeight}",
                "${PRESET_16_10.portraitWidth}x${PRESET_16_10.portraitHeight}"
            )
            if (overrideSizeMatch?.groupValues?.get(1) in knownPresets && prefs.contains(KEY_BACKUP_SIZE)) {
                Log.i(TAG, "Active override matches known stretch preset; keeping existing backup.")
                return
            }

            prefs.edit()
                .putString(KEY_BACKUP_SIZE, backupSize)
                .putInt(KEY_BACKUP_DENSITY, backupDensity)
                .putBoolean(KEY_HAD_OVERRIDE, hadOverride)
                .apply()

            Log.i(TAG, "Display state backed up: size=$backupSize, density=$backupDensity, hadOverride=$hadOverride")
        } catch (e: Throwable) {
            Log.e(TAG, "Error backing up display state: ${e.message}", e)
        }
    }

    /**
     * Helper to read the currently backed up display state.
     * Returns Triple(backupSize, backupDensity, hadOverride).
     */
    fun getBackupDisplayState(context: Context): Triple<String?, Int, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val size = prefs.getString(KEY_BACKUP_SIZE, null)
        val density = prefs.getInt(KEY_BACKUP_DENSITY, -1)
        val hadOverride = prefs.getBoolean(KEY_HAD_OVERRIDE, false)
        return Triple(size, density, hadOverride)
    }

    /**
     * Applies the true stretched resolution without black bars.
     * Sequence:
     * 1. Backup current display state
     * 2. Letterbox API override (kills black borders/pillarboxing)
     * 3. Freeform support & force resizable activities
     * 4. WindowManager scaling auto
     * 5. Apply custom Portrait dimensions (so Android rotates cleanly to landscape)
     * 6. Synchronize DPI to maintain touch mapping and UI scale
     */
    fun applyTrueStretch(context: Context, preset: ResolutionPreset): Boolean {
        backupCurrentDisplayState(context)
        val success = applyTrueStretchInternal(preset)
        if (success) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_STRETCHED, true)
                    .apply()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to persist is_stretched flag: ${e.message}")
            }
        }
        return success
    }

    /**
     * Overload for calls that do not supply a Context.
     */
    fun applyTrueStretch(preset: ResolutionPreset): Boolean {
        return applyTrueStretchInternal(preset)
    }

    private fun applyTrueStretchInternal(preset: ResolutionPreset): Boolean {
        try {
            if (!ShizukuManager.isAvailable() || !ShizukuManager.hasPermission()) {
                Log.w(TAG, "Shizuku not available or lacks permission in applyTrueStretch")
                return false
            }
            Log.i(TAG, "Applying True Stretch: ${preset.portraitWidth}x${preset.portraitHeight} @ ${preset.density} DPI")

            // 1. Android WindowManager Letterbox Style Override
            ShizukuManager.exec("cmd window set-letterbox-style --aspectRatio 1.33")
            ShizukuManager.exec("cmd window set-letterbox-style --cornerRadius 0")
            ShizukuManager.exec("cmd window set-letterbox-style --isLetterboxActivityCornersRounded false")
            ShizukuManager.exec("cmd window set-letterbox-style --backgroundType solid_color --backgroundColor 0x00000000")

            // 2. WindowManager Scaling & Freeform Settings
            ShizukuManager.exec("settings put global enable_freeform_support 1")
            ShizukuManager.exec("settings put secure force_resizable_activities 1")
            ShizukuManager.exec("cmd window scaling auto")

            // 3. Override Display Metrics in PORTRAIT
            val sizeResult = ShizukuManager.exec("wm size ${preset.portraitWidth}x${preset.portraitHeight}")
            val densityResult = ShizukuManager.exec("wm density ${preset.density}")

            Log.i(TAG, "wm size: $sizeResult | wm density: $densityResult")
            return !sizeResult.startsWith("ERR") && !densityResult.startsWith("ERR")
        } catch (e: Throwable) {
            Log.e(TAG, "Error applying true stretch: ${e.message}", e)
            return false
        }
    }

    /**
     * Restores S25 Ultra to native factory resolution or the user's pre-game custom resolution mode.
     */
    @JvmOverloads
    fun resetToNative(context: Context? = null): Boolean {
        Log.i(TAG, "Resetting display to native/original parameters")
        try {
            if (!ShizukuManager.isAvailable() || !ShizukuManager.hasPermission()) {
                Log.w(TAG, "Shizuku not available or lacks permission during resetToNative")
                return false
            }

            var hadOverride = false
            var backupSize: String? = null
            var backupDensity = -1

            if (context != null) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                hadOverride = prefs.getBoolean(KEY_HAD_OVERRIDE, false)
                backupSize = prefs.getString(KEY_BACKUP_SIZE, null)
                backupDensity = prefs.getInt(KEY_BACKUP_DENSITY, -1)
                prefs.edit().putBoolean(KEY_IS_STRETCHED, false).apply()
            }

            val r1: String
            val r2: String
            if (hadOverride && !backupSize.isNullOrEmpty() && backupDensity > 0) {
                Log.i(TAG, "Restoring user's pre-game custom display mode: $backupSize @ ${backupDensity} DPI")
                r1 = ShizukuManager.exec("wm size $backupSize")
                r2 = ShizukuManager.exec("wm density $backupDensity")
            } else {
                Log.i(TAG, "Restoring native S25 Ultra factory resolution (wm size reset, wm density reset)")
                r1 = ShizukuManager.exec("wm size reset")
                r2 = ShizukuManager.exec("wm density reset")
            }

            ShizukuManager.exec("cmd window reset-letterbox-style")
            ShizukuManager.exec("cmd window scaling auto")
            ShizukuManager.exec("settings put system min_refresh_rate 120.0")

            return !r1.startsWith("ERR") && !r2.startsWith("ERR")
        } catch (e: Throwable) {
            Log.e(TAG, "Error in resetToNative: ${e.message}", e)
            return false
        }
    }

    /**
     * Launches the game reliably by resolving its component or using Android's monkey launcher.
     */
    fun launchGame(packageName: String, componentName: String? = null): Boolean {
        try {
            if (!ShizukuManager.isAvailable() || !ShizukuManager.hasPermission()) {
                Log.w(TAG, "Shizuku not available or lacks permission to launch game")
                return false
            }
            val cmd = if (!componentName.isNullOrEmpty()) {
                "am start --windowingMode 1 -n $componentName"
            } else {
                "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
            }
            val launchResult = ShizukuManager.exec(cmd)
            Log.i(TAG, "Launch command [$cmd] result: $launchResult")
            return !launchResult.startsWith("ERR")
        } catch (e: Throwable) {
            Log.e(TAG, "Error launching game $packageName: ${e.message}", e)
            return false
        }
    }
}
