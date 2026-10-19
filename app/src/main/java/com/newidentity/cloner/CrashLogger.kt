package com.newidentity.cloner

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Har crash ko Downloads folder me .txt file ke roop me save karta hai.
 * Laptop / Android Studio ki zaroorat nahi.
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val where = save(appContext, thread, error)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appContext, "Crash log saved:\n$where", Toast.LENGTH_LONG).show()
                }
                Thread.sleep(2500) // toast dikhne ka time
            } catch (ignored: Throwable) {
                // crash handler me crash nahi karna
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(context: Context, thread: Thread, error: Throwable): String {
        val text = buildReport(context, thread, error)
        val fileName = "CloneVersePro_crash_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"

        // 1) Android 10+ : MediaStore se seedha Downloads me
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(text.toByteArray(Charsets.UTF_8))
                    }
                    return "Downloads/$fileName"
                }
            } catch (ignored: Throwable) {
            }
        }

        // 2) Purane Android (7-9) : public Downloads
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(text)
                return "Downloads/$fileName"
            }
        } catch (ignored: Throwable) {
        }

        // 3) Last fallback : app ka apna folder
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val f = File(dir, fileName)
            f.writeText(text)
            return f.absolutePath
        } catch (ignored: Throwable) {
        }

        return "(save nahi hua)"
    }

    private fun buildReport(context: Context, thread: Thread, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Throwable) {
            "?"
        }

        return buildString {
            appendLine("=== CloneVersePro CRASH REPORT ===")
            appendLine("time    : " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("device  : " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("android : API " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")")
            appendLine("abis    : " + Build.SUPPORTED_ABIS.joinToString(", "))
            appendLine("app     : " + context.packageName + " v" + versionName)
            appendLine("thread  : " + thread.name)
            appendLine("---------------------------------")
            appendLine(sw.toString())
            appendLine("=== END ===")
        }
    }
}
