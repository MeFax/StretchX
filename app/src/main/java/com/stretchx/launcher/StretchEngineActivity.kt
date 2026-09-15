package com.stretchx.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stretchx.launcher.databinding.ActivityStretchEngineBinding
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

class StretchEngineActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "StretchEngineActivity"
        const val EXTRA_TARGET_PACKAGE = "EXTRA_TARGET_PACKAGE"
        const val EXTRA_TARGET_COMPONENT = "EXTRA_TARGET_COMPONENT"
        const val EXTRA_VIRT_WIDTH = "EXTRA_VIRT_WIDTH"
        const val EXTRA_VIRT_HEIGHT = "EXTRA_VIRT_HEIGHT"
        const val EXTRA_VIRT_DENSITY = "EXTRA_VIRT_DENSITY"
    }

    private lateinit var binding: ActivityStretchEngineBinding
    private var shellDisplayId: Int = -1

    private var targetPackage: String = ""
    private var targetComponent: String? = null
    private var virtWidth: Int = 1920
    private var virtHeight: Int = 1440
    private var virtDensity: Int = 440

    private var pendingSurface: Surface? = null
    private var stretchService: IStretchService? = null
    private var userServiceArgs: UserServiceArgs? = null
    private var serviceConnection: ServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var launchAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemUI()

        binding = ActivityStretchEngineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
        targetComponent = intent.getStringExtra(EXTRA_TARGET_COMPONENT)
        virtWidth = intent.getIntExtra(EXTRA_VIRT_WIDTH, 1920)
        virtHeight = intent.getIntExtra(EXTRA_VIRT_HEIGHT, 1440)
        virtDensity = intent.getIntExtra(EXTRA_VIRT_DENSITY, 440)

        binding.surfaceStretch.holder.addCallback(this)
        binding.surfaceStretch.holder.setFormat(PixelFormat.RGBX_8888)

        binding.btnExitStretch.setOnClickListener {
            finish()
        }

        setupTouchRouting()
        ShellDisplayManager.bind(this) { service ->
            stretchService = service
            tryCreateShellDisplay()
        }.also {
            userServiceArgs = it.first
            serviceConnection = it.second
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchRouting() {
        binding.surfaceStretch.setOnTouchListener { view, event ->
            if (shellDisplayId <= 0) return@setOnTouchListener false

            val surfaceWidth = view.width.toFloat()
            val surfaceHeight = view.height.toFloat()
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return@setOnTouchListener false

            val scaleX = virtWidth.toFloat() / surfaceWidth
            val scaleY = virtHeight.toFloat() / surfaceHeight

            InputInjector.injectScaledTouch(event, shellDisplayId, scaleX, scaleY)
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "Surface created ${holder.surfaceFrame.width()}x${holder.surfaceFrame.height()}. Requesting shell-owned 4:3 display.")
        pendingSurface = holder.surface
        tryCreateShellDisplay()
    }

    private fun tryCreateShellDisplay() {
        val surface = pendingSurface ?: return
        val service = stretchService ?: return
        if (shellDisplayId > 0 || launchAttempted) return
        mainHandler.post {
            try {
                val id = service.createDisplay(virtWidth, virtHeight, virtDensity, surface)
                shellDisplayId = id
                Log.i(TAG, "Shell-owned VirtualDisplay id=$id ${virtWidth}x${virtHeight}@${virtDensity}dpi")
                if (id > 0 && targetPackage.isNotEmpty()) {
                    launchAttempted = true
                    launchTargetGameOnVirtualDisplay(id)
                } else if (id <= 0) {
                    Log.e(TAG, "Shell createDisplay returned invalid id=$id")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Shell createDisplay IPC failed", e)
            }
        }
    }

    private fun launchTargetGameOnVirtualDisplay(displayId: Int) {
        var effectiveComponent = targetComponent
        if (effectiveComponent.isNullOrEmpty()) {
            effectiveComponent = try {
                packageManager.getLaunchIntentForPackage(targetPackage)?.component?.flattenToString()
            } catch (e: Throwable) {
                Log.e(TAG, "Component resolve failed for $targetPackage", e)
                null
            }
            if (!effectiveComponent.isNullOrEmpty()) {
                targetComponent = effectiveComponent
                Log.i(TAG, "Resolved component for $targetPackage: $effectiveComponent")
            }
        }
        if (effectiveComponent.isNullOrEmpty()) {
            Log.e(TAG, "Launch ABORTED: no concrete component for $targetPackage. Refusing silent Display 0 fallback.")
            android.widget.Toast.makeText(this, "Oyun aktivitesi çözülemedi, başlatma iptal edildi.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val cmd = "am start --display $displayId -n $effectiveComponent"
        Log.i(TAG, "Launching game on shell-owned display [$displayId]: $cmd")
        val result = ShizukuManager.exec(cmd)
        Log.i(TAG, "Launch result: $result")
        mainHandler.postDelayed({
            verifyGameOnDisplay(displayId)
        }, 2500)
    }

    private fun verifyGameOnDisplay(displayId: Int) {
        val dump = ShizukuManager.exec("dumpsys activity activities | grep -E 'displayId=$displayId|topResumedActivity'")
        Log.i(TAG, "Display $displayId verification dump: $dump")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: width=$width, height=$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "Surface destroyed. Releasing shell-owned Virtual Display.")
        pendingSurface = null
        releaseShellDisplay()
    }

    private fun releaseShellDisplay() {
        try {
            stretchService?.releaseDisplay()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing shell display", e)
        }
        shellDisplayId = -1
        launchAttempted = false
    }

    private fun destroyShellService() {
        try {
            stretchService?.destroy()
        } catch (e: Throwable) {
            Log.e(TAG, "destroy() IPC failed", e)
        }
        try {
            val args = userServiceArgs
            val conn = serviceConnection
            if (args != null && conn != null) {
                Shizuku.unbindUserService(args, conn, true)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "unbindUserService failed", e)
        }
        stretchService = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseShellDisplay()
        destroyShellService()
    }
}
