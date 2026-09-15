package com.stretchx.launcher

import android.graphics.Matrix
import android.os.IBinder
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object InputInjector {
    private const val TAG = "InputInjector"

    private var injectMethod: Method? = null
    private var iInputManagerInstance: Any? = null
    private var setDisplayIdMethod: Method? = null

    init {
        initBinder()
    }

    fun isReady(): Boolean {
        if (iInputManagerInstance == null || injectMethod == null) {
            initBinder()
        }
        return iInputManagerInstance != null && injectMethod != null
    }

    private fun initBinder() {
        if (!ShizukuManager.hasPermission()) return

        try {
            val binder: IBinder? = SystemServiceHelper.getSystemService("input")
            if (binder != null && binder.pingBinder()) {
                val wrappedBinder = ShizukuBinderWrapper(binder)
                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
                iInputManagerInstance = asInterfaceMethod.invoke(null, wrappedBinder)

                // injectInputEvent(InputEvent event, int mode)
                injectMethod = iInputManagerInstance?.javaClass?.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )?.apply {
                    isAccessible = true
                }

                // Cache MotionEvent.setDisplayId method
                try {
                    setDisplayIdMethod = MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)?.apply {
                        isAccessible = true
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "MotionEvent.setDisplayId not accessible via reflection", e)
                }

                Log.i(TAG, "Shizuku IInputManager binder initialized successfully.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize IInputManager binder via Shizuku", e)
        }
    }

    /**
     * Injects a touch event into the target display ID with asynchronous execution (mode 0).
     * Uses Matrix transform to accurately scale ALL pointers simultaneously for 4-finger claw multi-touch.
     */
    fun injectScaledTouch(
        event: MotionEvent,
        targetDisplayId: Int,
        scaleFactorX: Float,
        scaleFactorY: Float
    ): Boolean {
        if (!isReady()) return false

        return try {
            val clonedEvent = MotionEvent.obtain(event)

            // 1. Transform ALL pointers simultaneously (Multi-touch / Claw grip safe)
            val matrix = Matrix().apply {
                setScale(scaleFactorX, scaleFactorY)
            }
            clonedEvent.transform(matrix)

            // 2. Route event directly to the Virtual Display ID
            if (setDisplayIdMethod != null) {
                setDisplayIdMethod?.invoke(clonedEvent, targetDisplayId)
            }

            // 3. Inject asynchronously (mode 0 = INPUT_EVENT_INJECTION_SYNC_NONE, zero latency)
            val success = injectMethod?.invoke(iInputManagerInstance, clonedEvent, 0) as? Boolean ?: false

            clonedEvent.recycle()
            success
        } catch (e: Throwable) {
            Log.e(TAG, "Error injecting scaled touch event", e)
            false
        }
    }
}
