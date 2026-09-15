package com.stretchx.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

/**
 * Binds StretchUserService as UID 2000 (shell) so the virtual display is
 * created by the shell owner. Any game UID can launch on it via
 * `am start --display <id>`.
 */
object ShellDisplayManager {
    private const val TAG = "ShellDisplayManager"

    fun bind(
        context: Context,
        onConnected: (IStretchService) -> Unit
    ): Pair<UserServiceArgs, ServiceConnection> {
        val args = UserServiceArgs(
            ComponentName(context.packageName, StretchUserService::class.java.name)
        )
            .processNameSuffix("stretch_shell")
            .debuggable(true)
            .version(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    try {
                        val service = IStretchService.Stub.asInterface(binder)
                        Log.i(TAG, "StretchUserService connected as shell.")
                        onConnected(service)
                    } catch (e: Throwable) {
                        Log.e(TAG, "asInterface failed", e)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "StretchUserService disconnected.")
            }
        }

        if (!ShizukuManager.hasPermission()) {
            Log.e(TAG, "Shizuku permission missing; UserService bind skipped.")
        } else {
            Shizuku.bindUserService(args, connection)
        }
        return args to connection
    }
}
