package com.newidentity.cloner.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.apksig.ApkSigner
import com.google.gson.GsonBuilder
import com.newidentity.cloner.model.CloneOptions
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Clone Engine v2 — v1 ki "clone run nahi hota" wali khamiyon ki islah:
 *  1) ZIP ALIGNMENT: resources.arsc 4-byte, .so files 4096-byte align hoti hain
 *     (v1 mein yeh nahi tha - is liye cloned apps crash hoti theen).
 *  2) STORED entries STORED hi rehti hain (native libs kharab nahi hoti).
 *  3) STREAMING copy: poori file RAM mein load nahi hoti (baray APK par OOM nahi).
 *  4) resources.arsc sirf sahi jagah patch hota hai.
 *  5) Device Runtime dex inject (jab app supported ho).
 */
object CloneEngine {

    data class CloneResult(
        val clonedPackage: String,
        /** Pehli file base APK hai, baaki split APKs (agar app split thi) */
        val apkFiles: List<File>,
        val warnings: String, val runtimeInjected: Boolean
    ) {
        val apkFile: File get() = apkFiles.first()
    }

    fun makeClonePackage(context: Context, oldPkg: String, cloneNumber: Int): String {
        val chars = oldPkg.toCharArray()
        val lastSegStart = oldPkg.lastIndexOf('.') + 1
        val segLen = oldPkg.length - lastSegStart
        for (attempt in 0..35) {
            val c = chars.copyOf()
            val suffix = cloneNumber + attempt
            val suffixChar = when {
                suffix in 1..9 -> '0' + suffix
                suffix in 10..35 -> 'a' + (suffix - 10)
                else -> 'x'
            }
            if (segLen >= 2) { c[c.size - 2] = 'c'; c[c.size - 1] = suffixChar }
            else c[c.size - 1] = suffixChar
            val candidate = String(c)
            if (candidate == oldPkg) continue
            try { context.packageManager.getPackageInfo(candidate, 0) }
            catch (e: Exception) { return candidate }
        }
        val c = chars.copyOf(); c[c.size - 1] = if (c[c.size - 1] == 'z') 'y' else 'z'
        return String(c)
    }

    fun cloneApp(
        context: Context, appInfo: ApplicationInfo, options: CloneOptions,
        onStage: (String) -> Unit
    ): CloneResult {
        val baseApk = File(appInfo.sourceDir)
        require(baseApk.exists()) { "Original APK nahi mili" }
        // Split APKs (App Bundle) bhi saath clone karni hain, warna install fail / app crash hoti hai.
        val sources = ArrayList<File>()
        sources.add(baseApk)
        appInfo.splitSourceDirs?.forEach { p ->
            val f = File(p); if (f.exists()) sources.add(f)
        }

        val warnings = StringBuilder()
        val clonedPackage = makeClonePackage(context, options.sourcePackage, options.cloneNumber)
        val outDir = File(File(context.getExternalFilesDir(null) ?: context.filesDir, "clones"), clonedPackage)
        outDir.deleteRecursively(); outDir.mkdirs()

        onStage("Package badla ja raha hai:\n${options.sourcePackage}\n↓\n$clonedPackage")

        val breakPerms = HashSet<String>()
        if (options.stripPhonePermissions) breakPerms += setOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.READ_PRIVILEGED_PHONE_STATE",
            "android.permission.READ_SMS"
        )
        if (options.stripAdIdPermission) breakPerms += "com.google.android.gms.permission.AD_ID"
        if (options.stripLocationPermission) breakPerms += setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_MEDIA_LOCATION"
        )

        val outFiles = ArrayList<File>()
        var runtimeInjected = false
        for ((idx, src) in sources.withIndex()) {
            val isBase = idx == 0
            val label = if (sources.size > 1) " (${idx + 1}/${sources.size})" else ""
            val unsigned = File(context.cacheDir, "clone_unsigned_${System.currentTimeMillis()}_$idx.apk")
            val signed = File(outDir, if (isBase) "base.apk" else "split_$idx.apk")
            try {
                val injected = patchApk(
                    context, src, unsigned, options, clonedPackage, breakPerms, isBase,
                    { stage -> onStage("$stage$label") }
                )
                if (isBase) runtimeInjected = injected
                onStage("Nayi key se SIGN ho raha hai...$label")
                signApk(context, unsigned, signed)
            } finally {
                unsigned.delete()
            }
            outFiles.add(signed)
        }
        if (sources.size > 1) warnings.appendLine("Split APK app thi: ${sources.size} files clone hui hain.")
        if (runtimeInjected) warnings.appendLine("Device Runtime inject ho gaya: clone ko ${options.identity.device.label} ki pehchan milegi.")
        return CloneResult(clonedPackage, outFiles, warnings.toString(), runtimeInjected)
    }

    // ---------------- APK Patch (streaming + aligned) ----------------

    private fun patchApk(
        context: Context, input: File, output: File, options: CloneOptions,
        newPkg: String, breakPerms: Set<String>, isBase: Boolean, onStage: (String) -> Unit
    ): Boolean {
        val oldPkg = options.sourcePackage
        var runtimeInjected = false
        var maxDex = 1

        val counting = CountingOutputStream(
            BufferedOutputStream(FileOutputStream(output), 256 * 1024)
        )
        ZipOutputStream(counting).apply { setLevel(1) }.use { zos ->
            ZipFile(input).use { zip ->
                // Pehle Manifest patch kar lo (runtime inject hoga ya nahi, yehi batayega)
                var manifestBytes: ByteArray? = null
                zip.getEntry("AndroidManifest.xml")?.let { e ->
                    val raw = zip.getInputStream(e).use { it.readBytes() }
                    val res = BinaryXmlPatcher.patchManifest(
                        raw, oldPkg, newPkg, breakPerms, options.useDeviceRuntime && isBase
                    )
                    // Clone ka naam: "WhatsApp 1", "WhatsApp 2" ... (sirf base APK mein)
                    manifestBytes = if (isBase)
                        BinaryXmlPatcher.patchLabel(res.bytes, "${options.sourceName} ${options.cloneNumber}")
                    else res.bytes
                    runtimeInjected = res.runtimeInjected
                }

                val entries = zip.entries()
                val totalEntries = zip.size().coerceAtLeast(1)
                var entryIndex = 0
                var lastPercent = -1
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryIndex++

                    // User ko asal progress dikhao, taaki lamba clone stuck na lage.
                    if (entryIndex == 1 || entryIndex % 100 == 0 || entryIndex == totalEntries) {
                        val percent = (entryIndex * 100) / totalEntries
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onStage("APK copy / patch ho rahi hai...\n$percent% dakhil ho gaya")
                        }
                    }

                    val name = entry.name
                    if (entry.isDirectory) continue
                    if (name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") ||
                         name.endsWith(".EC") || name.endsWith("MANIFEST.MF"))) continue
                    if (name == "assets/clone_identity.json") continue

                    dexNumber(name)?.let { if (it > maxDex) maxDex = it }

                    when (name) {
                        "AndroidManifest.xml" -> {
                            val data = manifestBytes ?: zip.getInputStream(entry).use { it.readBytes() }
                            writeBytesEntry(zos, counting, name, data, ZipEntry.DEFLATED)
                        }
                        "resources.arsc" -> {
                            val data = BinaryXmlPatcher.patchResources(
                                zip.getInputStream(entry).use { it.readBytes() }, oldPkg, newPkg
                            )
                            writeBytesEntry(zos, counting, name, data, ZipEntry.STORED)
                        }
                        else -> {
                            // Baaki sab streaming copy (method waisa hi rakho jaisa original mein tha)
                            zip.getInputStream(entry).use { inputStream ->
                                writeStreamEntry(zos, counting, entry, inputStream)
                            }
                        }
                    }
                }
            }

            if (isBase) {
                onStage("New Identity inject ho rahi hai (${options.identity.device.label})...")
                val json = GsonBuilder().setPrettyPrinting().create().toJson(options.identity)
                writeBytesEntry(zos, counting, "assets/clone_identity.json", json.toByteArray(Charsets.UTF_8), ZipEntry.DEFLATED)
            }

            if (runtimeInjected && isBase) {
                try {
                    val dex = context.assets.open("clone_runtime.dex").use { it.readBytes() }
                    val dexName = if (maxDex <= 1) "classes2.dex" else "classes${maxDex + 1}.dex"
                    writeBytesEntry(zos, counting, dexName, dex, ZipEntry.DEFLATED)
                } catch (e: Exception) {
                    runtimeInjected = false
                }
            }
        }
        return runtimeInjected
    }

    private fun dexNumber(name: String): Int? {
        return when {
            name == "classes.dex" -> 1
            name.matches(Regex("classes[0-9]+\\.dex")) ->
                name.removePrefix("classes").removeSuffix(".dex").toIntOrNull()
            else -> null
        }
    }

    /** ByteArray wali entry (manifest, arsc, json, runtime dex) */
    private fun writeBytesEntry(
        zos: ZipOutputStream, counting: CountingOutputStream,
        name: String, data: ByteArray, method: Int
    ) {
        val e = ZipEntry(name)
        e.method = method
        if (method == ZipEntry.STORED) {
            e.size = data.size.toLong(); e.compressedSize = data.size.toLong()
            val crc = CRC32(); crc.update(data); e.crc = crc.value
            addAlignment(e, counting.count, alignmentFor(name))
        }
        zos.putNextEntry(e)
        zos.write(data)
        zos.closeEntry()
    }

    /** Streaming entry - bara data RAM mein nahi ata */
    private fun writeStreamEntry(
        zos: ZipOutputStream, counting: CountingOutputStream,
        src: ZipEntry, input: InputStream
    ) {
        val name = src.name
        val e = ZipEntry(name)
        if (src.time > 0) e.time = src.time
        val stored = src.method == ZipEntry.STORED
        if (stored && src.size >= 0 && src.crc >= 0) {
            e.method = ZipEntry.STORED
            e.size = src.size; e.compressedSize = src.size; e.crc = src.crc
            addAlignment(e, counting.count, alignmentFor(name))
        } else {
            e.method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(e)
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            zos.write(buf, 0, n)
        }
        zos.closeEntry()
    }

    private fun alignmentFor(name: String): Int = when {
        name.endsWith(".so") -> 16384
        name == "resources.arsc" -> 4
        else -> 4
    }

    /**
     * ZIP local header ke baad data ko alignment par lane ke liye extra field padding.
     * Local header = 30 bytes + file name + extra.
     */
    private fun addAlignment(e: ZipEntry, currentOffset: Long, alignment: Int) {
        if (alignment <= 1) return
        val nameLen = e.name.toByteArray(Charsets.UTF_8).size
        val dataOffset = currentOffset + 30 + nameLen
        var padding = (alignment - (dataOffset % alignment).toInt()) % alignment
        if (padding == 0) return
        if (padding in 1..3) {
            padding += alignment
        }
        val extra = ByteArray(padding)
        // Extra field: ID 0xFFFF (2 bytes LE) + size (2 bytes LE) + zeros
        extra[0] = 0xFF.toByte(); extra[1] = 0xFF.toByte()
        val sz = padding - 4
        extra[2] = (sz and 0xFF).toByte(); extra[3] = ((sz shr 8) and 0xFF).toByte()
        e.extra = extra
    }

    /**
     * IMPORTANT FIX:
     * FilterOutputStream.write(ByteArray, ...) andar se write(Int) ko har byte par
     * call karta hai. Agar yahan super.write(...) use ho to poora APK aik aik karke
     * likha jata hai, jis ki wajah se 50-60 MB ki apps (masalan 2FAS) minutes tak
     * loading par atki hui lagti theen. Is liye seedha downstream par block-write karo.
     */
    private class CountingOutputStream(
        private val downstream: OutputStream
    ) : FilterOutputStream(downstream) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            downstream.write(b)
            count++
        }

        override fun write(b: ByteArray) {
            downstream.write(b)
            count += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            downstream.write(b, off, len)
            count += len
        }

        override fun flush() {
            downstream.flush()
        }

        override fun close() {
            downstream.close()
        }
    }

    // ---------------- Signing ----------------

    private fun signApk(context: Context, unsigned: File, signed: File) {
        val ksFile = File(context.cacheDir, "cloner.p12")
        if (!ksFile.exists()) {
            context.assets.open("cloner.p12").use { i -> FileOutputStream(ksFile).use { o -> i.copyTo(o) } }
        }
        val password = "androidclone".toCharArray()
        val ks = KeyStore.getInstance("PKCS12")
        FileInputStream(ksFile).use { ks.load(it, password) }
        val key = ks.getKey("cloner", password) as PrivateKey
        val certs = ks.getCertificateChain("cloner").map { it as X509Certificate }
        val cfg = ApkSigner.SignerConfig.Builder("cloner", key, certs).build()
        ApkSigner.Builder(listOf(cfg))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(24)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setCreatedBy("CloneVerse Pro - App Cloner v2")
            .build()
            .sign()
    }
}
