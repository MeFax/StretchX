package com.stretchx.launcher

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.RemoteException
import android.util.Log
import android.view.Surface

/**
 * Runs as UID 2000 (shell) inside Shizuku's UserService process.
 * Official pattern: extends the AIDL Stub directly (NO android.app.Service,
 * NO rikka.shizuku.SystemService -- neither exists in Shizuku API v13).
 * Creates the 4:3 virtual display as the SHELL owner, so any UID (PUBG,
 * Standoff 2, any game) can be launched on it via `am start --display`.
 */
class StretchUserService : IStretchService.Stub {

    constructor() : super()

    constructor(context: Context) : super() {
        appContext = context.applicationContext
    }

    companion object {
        private const val TAG = "StretchUserService"
        private const val FLAG_TRUSTED = 1 shl 10
    }

    @Volatile
    private var appContext: Context? = null

    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplayId: Int = -1

    private fun resolveContext(): Context? {
        appContext?.let { return it }
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApp = activityThread.getMethod("currentApplication").invoke(null) as? Context
            currentApp?.also { appContext = it.applicationContext }
        } catch (e: Throwable) {
            Log.e(TAG, "resolveContext failed", e)
            null
        }
    }

    @Throws(RemoteException::class)
    override fun createDisplay(width: Int, height: Int, densityDpi: Int, surface: Surface?): Int {
        return createShellOwnedDisplay(width, height, densityDpi, surface)
    }

    @Throws(RemoteException::class)
    override fun getDisplayId(): Int = virtualDisplayId

    @Throws(RemoteException::class)
    override fun releaseDisplay() {
        releaseShellDisplay()
    }

    @Throws(RemoteException::class)
    override fun destroy() {
        releaseShellDisplay()
        System.exit(0)
    }

    @Synchronized
    private fun createShellOwnedDisplay(width: Int, height: Int, densityDpi: Int, target: Surface?): Int {
        releaseShellDisplay()
        return try {
            val context = resolveContext()
                ?: return -1.also { Log.e(TAG, "No Context available in shell process") }
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    FLAG_TRUSTED
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
