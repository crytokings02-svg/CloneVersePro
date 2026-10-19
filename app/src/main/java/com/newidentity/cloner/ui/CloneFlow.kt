package com.newidentity.cloner.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.newidentity.cloner.engine.ApkInstaller
import com.newidentity.cloner.engine.CloneEngine
import com.newidentity.cloner.engine.CloneStore
import com.newidentity.cloner.engine.IdentityGenerator
import com.newidentity.cloner.model.CloneOptions
import com.newidentity.cloner.model.CloneRecord
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** Clone banana + install karna — Main page ka one-tap flow aur Settings screen dono yahi use karte hain. */
object CloneFlow {

    /** false ho to Settings khol deta hai (user ijazat de kar wapas aye aur dobara tap kare). */
    fun ensureInstallPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                activity,
                "Pehle is app ko 'Install unknown apps' ki ijazat do, phir wapas aa kar dobara tap karo",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return false
        }
        return true
    }

    /** Default settings (random device + nayi identity, SIM info hide) se clone banao aur seedha install karo. */
    @Suppress("DEPRECATION")
    fun quickClone(activity: Activity, appInfo: ApplicationInfo, appName: String) {
        if (!ensureInstallPermission(activity)) return

        val dialog = ProgressDialog(activity).apply {
            setTitle("Clone ban raha hai..."); setMessage("Tayyari..."); setCancelable(false); show()
        }
        thread {
            try {
                val identity = IdentityGenerator.newIdentity()
                val number = CloneStore.nextCloneNumber(activity, appInfo.packageName)
                val options = CloneOptions(
                    sourcePackage = appInfo.packageName, sourceName = appName, cloneNumber = number,
                    stripPhonePermissions = true, stripAdIdPermission = true, stripLocationPermission = true,
                    useDeviceRuntime = true, identity = identity
                )
                val result = CloneEngine.cloneApp(activity, appInfo, options) { stage ->
                    activity.runOnUiThread { dialog.setMessage(stage) }
                }
                val rec = CloneRecord(
                    id = UUID.randomUUID().toString(),
                    sourcePackage = appInfo.packageName, sourceName = appName,
                    clonedPackage = result.clonedPackage,
                    apkPath = result.apkFile.absolutePath,
                    apkPaths = result.apkFiles.map { it.absolutePath },
                    identity = identity, runtimeInjected = result.runtimeInjected
                )
                CloneStore.add(activity, rec)
                activity.runOnUiThread {
                    dialog.setMessage("Install ho raha hai...\n(Agar Android pooche to INSTALL dabao)")
                    dialog.setCancelable(true)
                    installRecord(activity, rec) { dialog.dismiss() }
                }
            } catch (e: Throwable) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    showError(activity, "Clone Fail Ho Gaya", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    /**
     * Ek record (base + splits) install karo. File copy background thread par hoti hai.
     * Khatam hone par onDone (main thread), phir askRun ho to RUN ka option.
     */
    fun installRecord(activity: Activity, rec: CloneRecord, askRun: Boolean = true, onDone: () -> Unit = {}) {
        val files = rec.allApkPaths().map { File(it) }
        if (files.any { !it.exists() }) {
            activity.runOnUiThread {
                onDone(); showError(activity, "Install Nahi Ho Saka", "APK file nahi mili - dobara Clone karein")
            }
            return
        }
        thread {
            ApkInstaller.install(activity, files) { ok, msg ->
                onDone()
                if (activity.isFinishing) return@install
                if (ok) { if (askRun) offerRun(activity, rec) } else showError(activity, "Install Fail", msg)
            }
        }
    }

    fun offerRun(activity: Activity, rec: CloneRecord) {
        val launch = activity.packageManager.getLaunchIntentForPackage(rec.clonedPackage)
        val b = AlertDialog.Builder(activity)
            .setTitle("Clone Install Ho Gaya!")
            .setMessage("${rec.sourceName} (Clone)\n${rec.clonedPackage}\nDevice: ${rec.identity.device.label}")
            .setNegativeButton("Baad Mein", null)
        if (launch != null) b.setPositiveButton("RUN CLONE") { _, _ -> activity.startActivity(launch) }
        b.show()
    }

    fun showError(activity: Activity, title: String, msg: String) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }
}
