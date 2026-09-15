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
    private var directGameComponent: String? = null
    private var virtWidth: Int = 1920
    private var virtHeight: Int = 1440
    private var virtDensity: Int = 440

    private var pendingSurface: Surface? = null
    private var stretchService: IStretchService? = null
    private var userServiceArgs: UserServiceArgs? = null
    private var serviceConnection: ServiceConnection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var launchAttempted = false
    private var prevFreeform: String? = null
    private var prevForceResizable: String? = null
    private var settingsBackedUp = false
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
            effectiveComponent = resolveRealActivity(targetPackage)
            if (!effectiveComponent.isNullOrEmpty()) {
                targetComponent = effectiveComponent
                Log.i(TAG, "Resolved component for $targetPackage: $effectiveComponent")
                updateStatus("Aktivite: $effectiveComponent")
            }
        }
        if (effectiveComponent.isNullOrEmpty()) {
            updateStatus("HATA: Oyun aktivitesi cozulemedi, baslatma iptal.")
            Log.e(TAG, "Launch ABORTED: no concrete component for $targetPackage. Refusing silent Display 0 fallback.")
            Toast.makeText(this, "Oyun aktivitesi çözülemedi, başlatma iptal edildi.", Toast.LENGTH_LONG).show()
            return
        }
        backupGlobalSettings()
        ShizukuManager.exec("settings put global enable_freeform_support 1")
        ShizukuManager.exec("settings put global force_resizable_activities 1")
        ShizukuManager.exec("am compat enable 174042936 $targetPackage")
        ShizukuManager.exec("am force-stop $targetPackage")
        val cmd = "am start --display $displayId --activity-new-task --activity-multiple-task -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $effectiveComponent"
        Log.i(TAG, "Launching game on shell-owned display [$displayId]: $cmd")
        val result = ShizukuManager.exec(cmd)
        Log.i(TAG, "Launch result: $result")
        updateStatus("Oyun baslatma: ${result.take(180)}")
        // Wrapper/splash -> gercek aktivite hop'u icin iki asamali dogrulama.
        mainHandler.postDelayed({ verifyAndRecover(displayId, 1) }, 2500)
        mainHandler.postDelayed({ verifyAndRecover(displayId, 2) }, 7000)
    }

    private fun resolveRealActivity(pkg: String): String? {
        // 1. Sistem cozucuye sor; son satir esastir (tail -n 1 mantigi).
        val viaCmd = ShizukuManager.exec("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $pkg")
        Log.i(TAG, "resolve-activity dump: $viaCmd")
        val lines = viaCmd.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val candidate = lines.lastOrNull { it.matches(Regex("^[a-zA-Z0-9_.]+/[a-zA-Z0-9_.\$]+$")) }
        // ResolverActivity tuzagi: coklu LAUNCHER varsa sistem secici dondurur, gercek hedef degil.
        if (candidate != null && candidate.endsWith("/com.android.internal.app.ResolverActivity")) {
            Log.w(TAG, "resolve-activity returned ResolverActivity, rejecting: $candidate")
            updateStatus("Secici dondu, alias caprazi deneniyor...")
        } else if (!candidate.isNullOrEmpty()) {
            // 2. Alias capraz kontrol: activity-alias ise targetActivity'yi bul.
            val activityName = candidate.substringAfter("/")
            val pkgDump = ShizukuManager.exec("dumpsys package $pkg | grep -A3 -B3 '$activityName'")
            val target = Regex("""targetActivity=([a-zA-Z0-9_.\$]+)""").find(pkgDump)?.groupValues?.get(1)
            if (!target.isNullOrEmpty()) {
                val real = "$pkg/$target"
                Log.i(TAG, "alias $candidate -> target $real")
                updateStatus("Alias cozumu: $real")
                return real
            }
            Log.i(TAG, "resolve-activity -> $candidate")
            return candidate
        }
        // 3. Fallback: PackageManager launch intent.
        val launchComp = try {
            packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToString()
        } catch (e: Throwable) {
            Log.e(TAG, "Component resolve failed for $pkg", e)
            null
        }
        // 4. UE4 GameActivity adayini NOT ET ama birincil yapma: Splash engine/OBB init
        // yapar; Game'e direkt vurmak black/crash verip testi kirletebilir. Splash birincil,
        // Game sadece splash Display 0'a duserse verifyAndRecover icinde fallback denenir.
        if (launchComp != null) {
            val allActs = ShizukuManager.exec("dumpsys package $pkg | grep -E 'Activity|targetActivity' | grep -i -E 'game|ue4'")
            val gameAct = Regex("""([a-zA-Z0-9_.]+\.(GameActivity|UEGameActivity|Game))""").find(allActs)?.groupValues?.get(1)
                ?: Regex("""targetActivity=([a-zA-Z0-9_.\$]+Game[a-zA-Z0-9_.\$]*)""").find(allActs)?.groupValues?.get(1)
            if (!gameAct.isNullOrEmpty()) {
                val direct = if (gameAct.contains("/")) gameAct else "$pkg/$gameAct"
                Log.i(TAG, "GameActivity adayi (fallback): $direct (birincil: $launchComp)")
                updateStatus("Oyun adayi: $direct")
                directGameComponent = direct
            }
        }
        return launchComp
    }

    private fun verifyAndRecover(displayId: Int, round: Int) {
        if (round > 32) {
            updateStatus("Dogrulama bitti: oyun sanal ekrana yerlesmedi.")
            return
        }
        Thread {
            val dump = ShizukuManager.exec("dumpsys activity activities")
            Log.i(TAG, "Display $displayId verification dump (round $round): ${dump.take(2000)}")
            var currentDisplay: Int? = null
            var gameDisplay: Int? = null
            var gameTaskId: String? = null
            var pendingTaskId: String? = null
            var pendingDisplay: Int? = null
            for (rawLine in dump.lines()) {
                val line = rawLine.trim()
                Regex("""Display #(\d+)""").find(line)?.let {
                    currentDisplay = it.groupValues[1].toIntOrNull()
                    pendingTaskId = null
                    pendingDisplay = null
                }
                val taskMatch = Regex("""Task\{[^}]*#(\d+)""").find(line)
                if (taskMatch != null) {
                    pendingTaskId = taskMatch.groupValues[1]
                    pendingDisplay = Regex("""displayId=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                        ?: currentDisplay
                }
                if (line.contains(targetPackage) && (line.contains("Hist") || line.contains("packageName=") || line.contains("A="))) {
                    gameDisplay = pendingDisplay ?: currentDisplay
                    gameTaskId = pendingTaskId
                    break
                }
            }
            val taskId = gameTaskId
                ?: Regex("""taskId=(\d+)""").find(dump)?.groupValues?.get(1)
            mainHandler.post {
                updateStatus("Dogrulama($round): ${dump.take(240)}")
                if (gameDisplay == displayId) {
                    updateStatus("OK: Oyun sanal ekranda (id=$displayId).")
                    return@post
                }
                if (gameDisplay != null) {
                    updateStatus("Oyun Display $gameDisplay'de, hedef $displayId. Tasma deneniyor...")
                }
                if (taskId != null) {
                    updateStatus("Fallback goruldu($round), gorev $taskId sanal ekrana tasiniyor...")
                    Thread {
                        val move = ShizukuManager.exec("am display move-stack $taskId $displayId")
                        Log.i(TAG, "move-stack result: $move")
                        mainHandler.post {
                            updateStatus("Tasima sonucu($round): ${move.take(180)}")
                            // Splash Display 0'a dustuysa ve Game adayi varsa dogrudan dene.
                            val direct = directGameComponent
                            if (round >= 11 && !direct.isNullOrEmpty() && direct != targetComponent) {
                                updateStatus("Direkt oyun deneniyor: $direct")
                                Thread {
                                    val cmd2 = "am start --display $displayId --activity-new-task --activity-multiple-task -n $direct"
                                    val res2 = ShizukuManager.exec(cmd2)
                                    Log.i(TAG, "Direct game launch: $res2")
                                    mainHandler.post {
                                        updateStatus("Direkt sonuc: ${res2.take(180)}")
                                        mainHandler.postDelayed({ verifyAndRecover(displayId, round + 10) }, 4000)
                                    }
                                }.start()
                            } else {
                                mainHandler.postDelayed({ verifyAndRecover(displayId, round + 10) }, 4000)
                            }
                        }
                    }.start()
                } else {
                    updateStatus("UYARI($round): Oyun sanal ekranda gorunmuyor, gorev bulunamadi.")
                }
            }
        }.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: width=$width, height=$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "Surface destroyed. Releasing shell-owned Virtual Display.")
        pendingSurface = null
        releaseShellDisplay()
    }

    private fun backupGlobalSettings() {
        if (settingsBackedUp) return
        prevFreeform = ShizukuManager.exec("settings get global enable_freeform_support").takeIf { !it.startsWith("ERR") }
        prevForceResizable = ShizukuManager.exec("settings get global force_resizable_activities").takeIf { !it.startsWith("ERR") }
        settingsBackedUp = true
        Log.i(TAG, "Backed up globals: freeform=$prevFreeform forceResizable=$prevForceResizable")
    }

    private fun restoreGlobalSettings() {
        if (!settingsBackedUp) return
        if (prevFreeform.isNullOrEmpty() || prevFreeform == "null") {
            ShizukuManager.exec("settings delete global enable_freeform_support")
        } else {
            ShizukuManager.exec("settings put global enable_freeform_support $prevFreeform")
        }
        if (prevForceResizable.isNullOrEmpty() || prevForceResizable == "null") {
            ShizukuManager.exec("settings delete global force_resizable_activities")
        } else {
            ShizukuManager.exec("settings put global force_resizable_activities $prevForceResizable")
        }
        if (targetPackage.isNotEmpty()) {
            ShizukuManager.exec("am compat disable 174042936 $targetPackage")
        }
        settingsBackedUp = false
        Log.i(TAG, "Restored globals to backed-up values.")
    }

    private fun releaseShellDisplay() {
        try {
            stretchService?.releaseDisplay()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing shell display", e)
        }
        shellDisplayId = -1
        launchAttempted = false
        restoreGlobalSettings()
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
        mainHandler.removeCallbacksAndMessages(null)
        releaseShellDisplay()
        destroyShellService()
    }
}
