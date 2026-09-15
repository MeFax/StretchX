package com.stretchx.launcher

import android.annotation.SuppressLint
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
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
    private val statusLines = ArrayDeque<String>()

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
        updateStatus("1/4 Shizuku shell servisine baglaniliyor...")
        ShellDisplayManager.bind(this) { service ->
            stretchService = service
            updateStatus("2/4 Shell servisi baglandi (UID 2000).")
            tryCreateShellDisplay()
        }.also {
            userServiceArgs = it.first
            serviceConnection = it.second
        }
        mainHandler.postDelayed({
            if (shellDisplayId <= 0 && !isFinishing) {
                updateStatus("HATA: Sanal ekran acilamadi. Shizuku calisiyor mu, izin verildi mi?")
            }
        }, 9000)
    }

    private fun updateStatus(msg: String) {
        Log.i(TAG, msg)
        mainHandler.post {
            statusLines.addLast(msg)
            while (statusLines.size > 6) statusLines.removeFirst()
            binding.tvStretchStatus.text = statusLines.joinToString("\n")
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
        updateStatus("Yuzey hazir. Sanal ekran isteniyor...")
        pendingSurface = holder.surface
        tryCreateShellDisplay()
    }

    private fun tryCreateShellDisplay() {
        val surface = pendingSurface ?: return
        val service = stretchService ?: return
        if (shellDisplayId > 0 || launchAttempted) return
        updateStatus("3/4 Sanal ekran aciliyor ${virtWidth}x${virtHeight}...")
        mainHandler.post {
            try {
                val id = service.createDisplay(virtWidth, virtHeight, virtDensity, surface)
                shellDisplayId = id
                Log.i(TAG, "Shell-owned VirtualDisplay id=$id ${virtWidth}x${virtHeight}@${virtDensity}dpi")
                if (id <= 0) {
                    val err = try { service.lastError } catch (_: Throwable) { "" }
                    val flags = try { service.lastFlags } catch (_: Throwable) { "" }
                    val ctx = try { service.contextSource } catch (_: Throwable) { "" }
                    updateStatus("HATA: createDisplay id=$id. Bayrak=$flags | Baglam=$ctx | Hata=$err")
                    Log.e(TAG, "Shell createDisplay failed id=$id flags=$flags ctx=$ctx err=$err")
                    return@post
                }
                if (targetPackage.isEmpty()) {
                    updateStatus("HATA: Hedef paket bos.")
                    return@post
                }
                launchAttempted = true
                updateStatus("Sanal ekran hazir (id=$id). Oyun firlatiliyor...")
                launchTargetGameOnVirtualDisplay(id)
            } catch (e: Throwable) {
                updateStatus("HATA: Sanal ekran IPC basarisiz: ${e.message}")
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
            updateStatus("HATA: Oyun aktivitesi cozulemedi, baslatma iptal.")
            Log.e(TAG, "Launch ABORTED: no concrete component for $targetPackage. Refusing silent Display 0 fallback.")
            Toast.makeText(this, "Oyun aktivitesi çözülemedi, başlatma iptal edildi.", Toast.LENGTH_LONG).show()
            return
        }
        val cmd = "am start --display $displayId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --activity-single-top -n $effectiveComponent"
        Log.i(TAG, "Launching game on shell-owned display [$displayId]: $cmd")
        val result = ShizukuManager.exec(cmd)
        Log.i(TAG, "Launch result: $result")
        updateStatus("Oyun baslatma: ${result.take(180)}")
        mainHandler.postDelayed({
            verifyAndRecover(displayId)
        }, 2500)
    }

    private fun verifyAndRecover(displayId: Int) {
        val dump = ShizukuManager.exec("dumpsys activity activities | grep -B2 -A2 '$targetPackage'")
        Log.i(TAG, "Display $displayId verification dump: $dump")
        updateStatus("Dogrulama: ${dump.take(240)}")
        val onTarget = dump.lines().any { it.contains("displayId=$displayId") && it.contains(targetPackage) }
        if (onTarget) {
            updateStatus("OK: Oyun sanal ekranda (id=$displayId).")
            return
        }
        val taskId = Regex("""taskId=(\d+)""").findAll(dump)
            .map { it.groupValues[1] }
            .firstOrNull()
        if (taskId != null) {
            updateStatus("Fallback goruldu, gorev $taskId sanal ekrana tasiniyor...")
            // am stack move-task Android 10+'da kaldirildi; once modern komut, olmazsa legacy dene.
            var move = ShizukuManager.exec("am task move-task $taskId $displayId")
            if (move.contains("Unknown command", ignoreCase = true) || move.contains("Unknown cmd", ignoreCase = true)) {
                move = ShizukuManager.exec("am stack move-task $taskId $displayId")
            }
            Log.i(TAG, "move-task result: $move")
            updateStatus("Tasima sonucu: ${move.take(180)}")
        } else {
            updateStatus("UYARI: Oyun sanal ekranda gorunmuyor, gorev bulunamadi.")
        }
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
