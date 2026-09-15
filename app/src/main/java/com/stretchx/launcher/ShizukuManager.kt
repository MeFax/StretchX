package com.stretchx.launcher

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    const val REQUEST_CODE_SHIZUKU = 1001

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (!isAvailable()) {
            callback(false)
            return
        }

        if (hasPermission()) {
            callback(true)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == REQUEST_CODE_SHIZUKU) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    callback(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    /**
     * Executes a shell command with rootless privileged ADB permissions via Shizuku.
     * Uses reflection on newProcess to remain compatible across all Shizuku API versions.
     */
    fun exec(command: String): String {
        if (!hasPermission()) return "ERR_SHIZUKU_NO_PERMISSION"
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                sb.append("[ERR] ").append(line).append("\n")
            }
            val code = process.waitFor()
            if (code != 0) {
                sb.append("[EXIT=").append(code).append("]\n")
            }
            sb.toString().trim()
        } catch (e: Throwable) {
            "ERR: ${e.message}"
        }
    }
}
