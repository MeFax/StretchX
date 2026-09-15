package com.stretchx.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stretchx.launcher.databinding.ActivityStretchEngineBinding

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
    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplayId: Int = -1

    private var targetPackage: String = ""
    private var targetComponent: String? = null
    private var virtWidth: Int = 1920
    private var virtHeight: Int = 1440
    private var virtDensity: Int = 440

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
            if (virtualDisplayId <= 0) return@setOnTouchListener false

            val surfaceWidth = view.width.toFloat()
            val surfaceHeight = view.height.toFloat()
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return@setOnTouchListener false

            // Compute non-uniform scale factors:
            // S25 Ultra physical width (e.g. 3120) -> Virtual display width (1920)
            val scaleX = virtWidth.toFloat() / surfaceWidth
            val scaleY = virtHeight.toFloat() / surfaceHeight

            InputInjector.injectScaledTouch(event, virtualDisplayId, scaleX, scaleY)
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "Surface created. Initializing 4:3 Virtual Display: ${virtWidth}x${virtHeight} @ ${virtDensity} DPI")
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        try {
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            virtualDisplay = displayManager.createVirtualDisplay(
                "StretchX_43_Display",
                virtWidth,
                virtHeight,
                virtDensity,
                holder.surface,
                flags
            )

            virtualDisplayId = virtualDisplay?.display?.displayId ?: -1
            Log.i(TAG, "Virtual Display created successfully with ID: $virtualDisplayId")

            if (virtualDisplayId > 0 && targetPackage.isNotEmpty()) {
                launchTargetGameOnVirtualDisplay(virtualDisplayId)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating virtual display", e)
        }
    }

    private fun launchTargetGameOnVirtualDisplay(displayId: Int) {
        val cmd = if (!targetComponent.isNullOrEmpty()) {
            "am start --display $displayId -n $targetComponent"
        } else {
            "monkey --display $displayId -p $targetPackage -c android.intent.category.LAUNCHER 1"
        }
        Log.i(TAG, "Launching game on virtual display [$displayId]: $cmd")
        val result = ShizukuManager.exec(cmd)
        Log.i(TAG, "Launch result: $result")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: width=$width, height=$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "Surface destroyed. Releasing Virtual Display.")
        releaseVirtualDisplay()
    }

    private fun releaseVirtualDisplay() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            virtualDisplayId = -1
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing virtual display", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVirtualDisplay()
    }
}
