package com.newidentity.cloner.engine

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Cloned APK(s) ko PackageInstaller session se install karta hai.
 * - Split APKs (base + splits) EK hi session mein install hoti hain.
 * - Android ka asal error message wapas deta hai (pehle sirf chup-chaap fail hota tha).
 */
object ApkInstaller {

    private const val ACTION_PREFIX = "com.newidentity.cloner.INSTALL_RESULT."

    /** onResult(success, message) — main thread par call hota hai. */
    fun install(activity: Activity, files: List<File>, onResult: (Boolean, String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val deliver = { ok: Boolean, msg: String -> main.post { onResult(ok, msg) }; Unit }
        val app = activity.applicationContext
        val installer = app.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(files.sumOf { it.length() })
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                files.forEachIndexed { i, f ->
                    val name = if (i == 0) "base.apk" else "split_$i.apk"
                    f.inputStream().use { input ->
                        session.openWrite(name, 0, f.length()).use { out ->
                            input.copyTo(out, 256 * 1024)
                            session.fsync(out)
                        }
                    }
                }

                val action = ACTION_PREFIX + sessionId
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                if (confirm != null) {
                                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { activity.startActivity(confirm) }
                                    catch (e: Exception) { context.startActivity(confirm) }
                                }
                            }
                            PackageInstaller.STATUS_SUCCESS -> {
                                try { context.unregisterReceiver(this) } catch (e: Exception) {}
                                deliver(true, "")
                            }
                            else -> {
                                try { context.unregisterReceiver(this) } catch (e: Exception) {}
                                deliver(false, describe(status, msg))
                            }
                        }
                    }
                }
                ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(
                    app, sessionId, Intent(action).setPackage(app.packageName), flags
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            if (sessionId >= 0) try { installer.abandonSession(sessionId) } catch (ignored: Exception) {}
            deliver(false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun describe(status: Int, msg: String): String {
        val reason = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancel ho gayi"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install block hui (Play Protect / device policy)"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Is package se conflict (pehle se installed clone?). Purana clone uninstall karke dobara try karein"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Device ke saath incompatible APK"
            PackageInstaller.STATUS_FAILURE_INVALID -> "APK invalid hai"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Storage kam hai"
            else -> "Install fail"
        }
        return if (msg.isBlank()) reason else "$reason\n($msg)"
    }
}
