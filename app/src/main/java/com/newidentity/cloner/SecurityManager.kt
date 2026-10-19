package com.anticlone.security

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

object SecurityManager {

    init {
        System.loadLibrary("native-check")
    }

    external fun checkMapsNative(): Boolean

    fun isDeviceSafe(context: Context): Boolean {
        if (!checkPackageAndUid(context)) return false
        if (!checkRuntimeIdentity(context)) return false
        if (checkMapsNative()) return false // true means clone found
        if (isHookingDetected()) return false
        if (isEmulatorOrRooted(context)) return false
        // Play Integrity check server side karna hai
        return true
    }

    fun checkPackageAndUid(context: Context): Boolean {
        val originalPackage = "com.anticlone.app" // apna package yahan dalo
        if (context.packageName != originalPackage) return false
        // UID check - filesDir path virtual container me alag hota hai
        val filesDir = context.filesDir.absolutePath
        if (filesDir.contains("virtual") || filesDir.contains("parallel")) return false
        return true
    }

    fun checkRuntimeIdentity(context: Context): Boolean {
        val processName = getProcessName()
        val packageName = context.packageName
        if (processName != packageName && !processName.startsWith(packageName)) {
            return false
        }
        return true
    }

    fun isHookingDetected(): Boolean {
        try {
            throw Exception()
        } catch (e: Exception) {
            for (trace in e.stackTrace) {
                if (trace.className.contains("xposed") ||
                    trace.className.contains("substrate") ||
                    trace.className.contains("frida")) return true
            }
        }
        // Frida port check logic yahan add kar sakte ho
        return false
    }

    fun isEmulatorOrRooted(context: Context): Boolean {
        if (Build.FINGERPRINT.contains("generic") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.MODEL.contains("Emulator")) return true

        val suPaths = arrayOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/data/adb/magisk")
        if (suPaths.any { File(it).exists() }) return true
        return false
    }

    private fun getProcessName(): String {
        return try {
            val pid = Process.myPid()
            File("/proc/$pid/cmdline").readText().trim().replace("\u0000","")
        } catch (e: Exception) { "" }
    }
}
