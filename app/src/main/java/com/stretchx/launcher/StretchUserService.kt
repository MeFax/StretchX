package com.stretchx.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Surface

/**
 * Runs as UID 2000 (shell) inside Shizuku's UserService process.
 * Creates the 4:3 virtual display as the SHELL owner, so any UID (PUBG,
 * Standoff 2, any game) can be launched on it via `am start --display`.
 * Also exposes reflection-free Surface handover from the Activity.
 */
class StretchUserService : rikka.shizuku.SystemService() {

    companion object {
        private const val TAG = "StretchUserService"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplayId: Int = -1
    private var surface: Surface? = null

    inner class StretchBinder : IStretchService.Stub() {
        override fun createDisplay(width: Int, height: Int, densityDpi: Int, surface: Surface?): Int {
            return createShellOwnedDisplay(width, height, densityDpi, surface)
        }

        override fun getDisplayId(): Int = virtualDisplayId

        override fun releaseDisplay() {
            releaseShellDisplay()
        }

        override fun destroy() {
            releaseShellDisplay()
            System.exit(0)
        }
    }

    override fun onBind(intent: android.content.Intent?): android.os.IBinder {
        return StretchBinder()
    }

    @Synchronized
    private fun createShellOwnedDisplay(width: Int, height: Int, densityDpi: Int, target: Surface?): Int {
        releaseShellDisplay()
        return try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
            surface = target
            virtualDisplay = displayManager.createVirtualDisplay(
                "StretchX_43_Display",
                width,
                height,
                densityDpi,
                target,
                flags
            )
            virtualDisplayId = virtualDisplay?.display?.displayId ?: -1
            Log.i(TAG, "Shell-owned (UID ${android.os.Process.myUid()}) VirtualDisplay created: id=$virtualDisplayId ${width}x${height}@${densityDpi}dpi")
            virtualDisplayId
        } catch (e: Throwable) {
            Log.e(TAG, "Shell-owned createVirtualDisplay failed", e)
            -1
        }
    }

    @Synchronized
    private fun releaseShellDisplay() {
        try {
            virtualDisplay?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "releaseShellDisplay failed", e)
        }
        virtualDisplay = null
        virtualDisplayId = -1
    }
}
