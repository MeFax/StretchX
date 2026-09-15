package com.stretchx.launcher

import android.content.Context
import android.content.ContextWrapper
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
 *
 * Krit: DisplayManagerService.validatePackageName, Binder.getCallingUid()=2000
 * ile getOpPackageName()/getPackageName() eslesmesi ister. Shizuku'nun verdigi
 * Context bizim paketi dondurdugu icin "packageName must match the owner uid"
 * patlar. Cozum: scrcpy tarzi ContextWrapper ile paket adini spoof'la.
 */
class StretchUserService : IStretchService.Stub {

    constructor() : super()

    constructor(context: Context) : super() {
        appContext = context.applicationContext
    }

    companion object {
        private const val TAG = "StretchUserService"
        private const val FLAG_TRUSTED = 1 shl 10
        private const val SHELL_PACKAGE = "com.android.shell"
    }

    @Volatile
    private var appContext: Context? = null

    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplayId: Int = -1
    @Volatile
    private var lastFlagsUsed: String = "none"
    @Volatile
    private var lastError: String = ""
    @Volatile
    private var contextSource: String = "unknown"

    private fun resolveContext(): Context? {
        appContext?.let {
            contextSource = "ctor"
            return it
        }
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApp = activityThread.getMethod("currentApplication").invoke(null) as? Context
            if (currentApp != null) {
                contextSource = "ActivityThread.currentApplication"
                appContext = currentApp.applicationContext
                appContext
            } else {
                contextSource = "ActivityThread returned null"
                lastError = "Context NULL: ActivityThread.currentApplication() null dondu (app_process icinde beklenen durum)"
                Log.e(TAG, lastError)
                null
            }
        } catch (e: Throwable) {
            contextSource = "ActivityThread exception: ${e.javaClass.simpleName}"
            lastError = "Context EXC: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "resolveContext failed", e)
            null
        }
    }

    /** UID 2000 (shell) olarak paket adini spoof'layan wrapper. */
    private class ShellPackageContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName(): String = SHELL_PACKAGE
        override fun getOpPackageName(): String = SHELL_PACKAGE

        override fun getSystemService(name: String): Any? {
            if (Context.DISPLAY_SERVICE == name) {
                // DisplayManager'i base'den degil, bu wrapper uzerinden kur ki
                // DisplayManagerGlobal icine spoof edilmis paket adi islesin.
                return try {
                    val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java).apply {
                        isAccessible = true
                    }
                    ctor.newInstance(this)
                } catch (e: Throwable) {
                    Log.e(TAG, "DisplayManager reflection failed, falling back to base", e)
                    super.getSystemService(name)
                }
            }
            return super.getSystemService(name)
        }
    }

    @Throws(RemoteException::class)
    override fun createDisplay(width: Int, height: Int, densityDpi: Int, surface: Surface?): Int {
        return createShellOwnedDisplay(width, height, densityDpi, surface)
    }

    @Throws(RemoteException::class)
    override fun getDisplayId(): Int = virtualDisplayId

    @Throws(RemoteException::class)
    override fun getLastError(): String = lastError

    @Throws(RemoteException::class)
    override fun getLastFlags(): String = lastFlagsUsed

    @Throws(RemoteException::class)
    override fun getContextSource(): String = contextSource

    @Throws(RemoteException::class)
    override fun releaseDisplay() {
        releaseShellDisplay()
    }

    @Throws(RemoteException::class)
    override fun launchOnDisplay(displayId: Int, packageName: String?, component: String?): String {
        return try {
            val context = resolveContext()
                ?: return "ERR: no Context in shell process"
            val shellContext = ShellPackageContext(context)
            val comp = component?.takeIf { it.contains("/") }
                ?: return "ERR: empty component"
            val pkg = packageName?.takeIf { it.isNotEmpty() }
                ?: comp.substringBefore("/")
            val cls = comp.substringAfter("/")
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(pkg, cls)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = android.app.ActivityOptions.makeBasic().apply {
                setLaunchDisplayId(displayId)
            }
            shellContext.startActivity(intent, options.toBundle())
            "OK: startActivity $comp display=$displayId"
        } catch (e: SecurityException) {
            lastError = "launchOnDisplay denied: ${e.message}"
            Log.e(TAG, lastError, e)
            "ERR SecurityException: ${e.message}"
        } catch (e: Throwable) {
            lastError = "launchOnDisplay failed: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, lastError, e)
            "ERR ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    @Throws(RemoteException::class)
    override fun destroy() {
        releaseShellDisplay()
        System.exit(0)
    }

    @Synchronized
    private fun createShellOwnedDisplay(width: Int, height: Int, densityDpi: Int, target: Surface?): Int {
        releaseShellDisplay()
        val context = resolveContext()
            ?: return -1.also { Log.e(TAG, "No Context available in shell process") }
        val shellContext = ShellPackageContext(context)
        contextSource = "$contextSource + shell-spoof"
        val shellDisplayManager = shellContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // 1. Deneme: PUBLIC+PRESENTATION (en genis uyumluluk, TRUSTED SystemApi riski yok)
        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            lastFlagsUsed = "PUBLIC|PRESENTATION"
            virtualDisplay = shellDisplayManager.createVirtualDisplay(
                "StretchX_43_Display",
                width,
                height,
                densityDpi,
                target,
                flags
            )
            virtualDisplayId = virtualDisplay?.display?.displayId ?: -1
            if (virtualDisplayId > 0) {
                Log.i(TAG, "Shell-owned (UID ${android.os.Process.myUid()}) VirtualDisplay created: id=$virtualDisplayId ${width}x${height}@${densityDpi}dpi flags=PUBLIC|PRESENTATION")
                return virtualDisplayId
            }
        } catch (e: Throwable) {
            lastError = "PUBLIC|PRESENTATION failed: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "PUBLIC|PRESENTATION createVirtualDisplay failed", e)
        }
        // 2. Deneme (fallback): PUBLIC+PRESENTATION+TRUSTED
        return try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    FLAG_TRUSTED
            lastFlagsUsed = "PUBLIC|PRESENTATION|TRUSTED"
            virtualDisplay = shellDisplayManager.createVirtualDisplay(
                "StretchX_43_Display",
                width,
                height,
                densityDpi,
                target,
                flags
            )
            virtualDisplayId = virtualDisplay?.display?.displayId ?: -1
            Log.i(TAG, "Shell-owned (UID ${android.os.Process.myUid()}) VirtualDisplay created: id=$virtualDisplayId ${width}x${height}@${densityDpi}dpi flags=PUBLIC|PRESENTATION|TRUSTED")
            virtualDisplayId
        } catch (e: Throwable) {
            lastError = "TRUSTED fallback failed: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "TRUSTED fallback createVirtualDisplay failed", e)
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
