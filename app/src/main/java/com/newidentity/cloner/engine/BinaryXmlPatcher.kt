package com.newidentity.cloner.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AndroidManifest.xml (binary AXML) patcher — v2.
 *
 * Naya package purane BARABAR LENGTH ka hota hai (com.example.app -> com.example.ac1),
 * is liye string-pool offsets kharab nahi hotay.
 *
 * v2 mein mazeed:
 *  - Device Runtime injection: agar app mein androidx ka AppComponentFactory ho,
 *    to usay hamare runtime factory se badal diya jata hai (barabar length 38).
 *  - resources.arsc mein package sahi jagah (ResTable_package name) par badalta hai,
 *    poori file mein andha-dhund replace NAHI karta (v1 ki crash wali ghalti fix).
 */
object BinaryXmlPatcher {

    const val RUNTIME_FACTORY = "com.clone.runtime.IdentityFactoryFinal" // 38 chars
    private const val ANDROIDX_FACTORY = "androidx.core.app.CoreComponentFactory" // 38 chars

    data class ManifestResult(val bytes: ByteArray, val runtimeInjected: Boolean)

    private class StrPool(
        val utf8: Boolean,
        val strings: List<String?>,
        val dataPos: IntArray,
        val dataLen: IntArray
    )

    fun patchManifest(
        bytes: ByteArray, oldPkg: String, newPkg: String,
        breakPermissions: Set<String>, injectRuntime: Boolean
    ): ManifestResult {
        require(oldPkg.length == newPkg.length) { "Clone package ki length barabar hona lazmi hai" }
        require(RUNTIME_FACTORY.length == ANDROIDX_FACTORY.length)
        val out = bytes.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(off: Int) = buf.getShort(off).toInt() and 0xFFFF
        fun u32(off: Int) = buf.getInt(off)
        if (out.size < 8 || (u16(0) != 0x0003 && u16(0) != 0x0002)) return ManifestResult(bytes, false)

        var pool: StrPool? = null
        val replace = HashMap<Int, String>()
        var runtime = false

        var off = 8
        while (off + 8 <= out.size) {
            val type = u16(off); val headerSize = u16(off + 2); val size = u32(off + 4)
            if (size <= 0) break
            when (type) {
                0x0001 -> { // STRING POOL
                    val stringCount = u32(off + 8)
                    val flags = u32(off + 16)
                    val stringsStart = u32(off + 20)
                    val utf8 = (flags and (1 shl 8)) != 0
                    val strs = arrayOfNulls<String>(stringCount)
                    val pos = IntArray(stringCount); val len = IntArray(stringCount)
                    for (i in 0 until stringCount) {
                        val o = off + stringsStart + u32(off + headerSize + i * 4)
                        try {
                            if (utf8) {
                                var p = o
                                p += varLen8(out, p).second
                                val (byteLen, used) = varLen8(out, p); p += used
                                pos[i] = p; len[i] = byteLen
                                strs[i] = String(out, p, byteLen, Charsets.UTF_8)
                            } else {
                                var charLen = u16(o); var p = o + 2
                                if (charLen and 0x8000 != 0) { charLen = ((charLen and 0x7FFF) shl 16) or u16(p); p += 2 }
                                pos[i] = p; len[i] = charLen * 2
                                strs[i] = String(out, p, charLen * 2, Charsets.UTF_16LE)
                            }
                        } catch (e: Exception) { strs[i] = null }
                    }
                    pool = StrPool(utf8, strs.toList(), pos, len)
                }
                0x0102 -> { // START ELEMENT
                    val p = pool
                    if (p != null && size >= 36) {
                        val elemName = strAt(p, u32(off + 20))
                        val attrStart = u16(off + 24)
                        val attrSize = u16(off + 26).let { if (it == 0) 20 else it }
                        val attrCount = u16(off + 28)
                        var aOff = off + 16 + attrStart
                        for (a in 0 until attrCount) {
                            if (aOff + 20 > off + size) break
                            val attrName = strAt(p, u32(aOff + 4))
                            val raw = u32(aOff + 8)
                            val dataType = out[aOff + 15].toInt() and 0xFF
                            val strIdx = when {
                                raw != -1 -> raw
                                dataType == 0x03 -> u32(aOff + 16)
                                else -> -1
                            }
                            if (strIdx >= 0) {
                                val value = strAt(p, strIdx)
                                if (value != null) {
                                    when {
                                        elemName == "manifest" && attrName == "package" ->
                                            replace[strIdx] = newPkg

                                        elemName == "provider" && attrName == "authorities" ->
                                            // oldPkg authority ke SHURU mein hi nahi, beech mein bhi ho sakta hai
                                            // (masalan "com.github.razerdp.<pkg>.BasePopupInitializer")
                                            replace[strIdx] = value.replace(oldPkg, newPkg)

                                        (elemName == "permission" || elemName == "permission-group" || elemName == "permission-tree")
                                                && attrName == "name" && value.contains(oldPkg) ->
                                            replace[strIdx] = value.replace(oldPkg, newPkg)

                                        // Apni hi custom permission jo uses-permission mein bhi likhi hoti hai
                                        (elemName == "uses-permission" || elemName == "uses-permission-sdk-23")
                                                && attrName == "name" && value.contains(oldPkg) ->
                                            replace[strIdx] = value.replace(oldPkg, newPkg)

                                        (elemName == "uses-permission" || elemName == "uses-permission-sdk-23")
                                                && attrName == "name" && breakPermissions.contains(value) -> {
                                            val last = value.last()
                                            replace[strIdx] = value.dropLast(1) + (if (last == 'X') 'Z' else 'X')
                                        }

                                        // ★ Device Runtime: factory badal do (sirf AndroidX wali par)
                                        injectRuntime && elemName == "application" && attrName == "appComponentFactory"
                                                && value == ANDROIDX_FACTORY -> {
                                            replace[strIdx] = RUNTIME_FACTORY
                                            runtime = true
                                        }
                                    }
                                }
                            }
                            aOff += attrSize
                        }
                    }
                }
            }
            off += size
        }

        val p = pool ?: return ManifestResult(bytes, false)
        for ((idx, newValue) in replace) {
            val oldValue = p.strings.getOrNull(idx) ?: continue
            if (oldValue.length != newValue.length) continue
            val start = p.dataPos[idx]
            if (p.utf8) {
                val b = newValue.toByteArray(Charsets.UTF_8)
                if (b.size == p.dataLen[idx]) System.arraycopy(b, 0, out, start, b.size)
            } else {
                val b = newValue.toByteArray(Charsets.UTF_16LE)
                if (b.size == p.dataLen[idx]) System.arraycopy(b, 0, out, start, b.size)
            }
        }
        return ManifestResult(out, runtime)
    }

    /**
     * Clone ka naam badalna (masalan "WhatsApp 1"): application aur LAUNCHER activity ke
     * android:label ko seedha text (string) bana deta hai. Naya naam string pool ke aakhir mein
     * jodta hai (purani strings ki index nahi hilti). Kuch bhi gadbad ho to ORIGINAL bytes wapas.
     */
    fun patchLabel(bytes: ByteArray, newLabel: String): ByteArray {
        return try {
            patchLabelInternal(bytes, newLabel) ?: bytes
        } catch (e: Throwable) {
            bytes
        }
    }

    private fun patchLabelInternal(bytes: ByteArray, newLabel: String): ByteArray? {
        if (newLabel.isEmpty() || bytes.size < 8) return null
        val src = bytes.copyOf()
        val buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(o: Int) = buf.getShort(o).toInt() and 0xFFFF
        fun u32(o: Int) = buf.getInt(o)
        if (u16(0) != 0x0003) return null

        // Pehla chunk string pool hona chahiye
        val poolOff = 8
        if (u16(poolOff) != 0x0001) return null
        val poolHeader = u16(poolOff + 2)
        val poolSize = u32(poolOff + 4)
        val stringCount = u32(poolOff + 8)
        val styleCount = u32(poolOff + 12)
        val flags = u32(poolOff + 16)
        val stringsStart = u32(poolOff + 20)
        if (styleCount != 0 || poolHeader != 28 || poolOff + poolSize > src.size) return null
        val utf8 = (flags and (1 shl 8)) != 0

        val names = arrayOfNulls<String>(stringCount)
        for (i in 0 until stringCount) {
            val o = poolOff + stringsStart + u32(poolOff + poolHeader + i * 4)
            try {
                if (utf8) {
                    var p = o
                    p += varLen8(src, p).second
                    val (byteLen, used) = varLen8(src, p); p += used
                    names[i] = String(src, p, byteLen, Charsets.UTF_8)
                } else {
                    var charLen = u16(o); var p = o + 2
                    if (charLen and 0x8000 != 0) { charLen = ((charLen and 0x7FFF) shl 16) or u16(p); p += 2 }
                    names[i] = String(src, p, charLen * 2, Charsets.UTF_16LE)
                }
            } catch (e: Exception) { names[i] = null }
        }
        fun nameAt(i: Int): String? = if (i in 0 until stringCount) names[i] else null

        val newIdx = stringCount
        var patched = 0
        var launcherLabelAttr = -1   // abhi wali activity ke label attribute ka offset
        val patchedAttrs = HashSet<Int>()

        fun setLabel(attrOff: Int) {
            if (!patchedAttrs.add(attrOff)) return
            buf.putInt(attrOff + 8, newIdx)          // rawValue
            src[attrOff + 15] = 0x03                  // dataType = string
            buf.putInt(attrOff + 16, newIdx)         // data
            patched++
        }

        var off = poolOff + poolSize
        while (off + 8 <= src.size) {
            val type = u16(off); val size = u32(off + 4)
            if (size <= 0 || off + size > src.size) return null
            if (type == 0x0102 && size >= 36) {
                val elem = nameAt(u32(off + 20))
                val attrStart = u16(off + 24)
                val attrSize = u16(off + 26).let { if (it == 0) 20 else it }
                val attrCount = u16(off + 28)
                var labelAttr = -1
                var catValue: String? = null
                var aOff = off + 16 + attrStart
                for (a in 0 until attrCount) {
                    if (aOff + 20 > off + size) break
                    val an = nameAt(u32(aOff + 4))
                    if (an == "label") labelAttr = aOff
                    if (elem == "category" && an == "name") {
                        val raw = u32(aOff + 8)
                        catValue = nameAt(if (raw != -1) raw else u32(aOff + 16))
                    }
                    aOff += attrSize
                }
                when (elem) {
                    "application" -> if (labelAttr >= 0) setLabel(labelAttr)
                    "activity", "activity-alias" -> launcherLabelAttr = labelAttr
                    "category" ->
                        if (catValue == "android.intent.category.LAUNCHER" && launcherLabelAttr >= 0)
                            setLabel(launcherLabelAttr)
                }
            }
            off += size
        }
        if (patched == 0) return null

        // Naya string encode karo
        val enc: ByteArray = if (utf8) {
            val b = newLabel.toByteArray(Charsets.UTF_8)
            if (newLabel.length > 127 || b.size > 127) return null
            byteArrayOf(newLabel.length.toByte(), b.size.toByte()) + b + byteArrayOf(0)
        } else {
            val b = newLabel.toByteArray(Charsets.UTF_16LE)
            if (newLabel.length > 0x7FFF) return null
            byteArrayOf((newLabel.length and 0xFF).toByte(), ((newLabel.length shr 8) and 0xFF).toByte()) +
                b + byteArrayOf(0, 0)
        }

        // Naya pool: header + (offsets + 1 naya) + purana string data + naya string, 4 byte padded
        val oldOffsetsEnd = poolOff + poolHeader + stringCount * 4
        val oldDataStart = poolOff + stringsStart
        val oldDataEnd = poolOff + poolSize
        val oldData = src.copyOfRange(oldDataStart, oldDataEnd)
        val newStringRel = oldData.size
        var newPoolSize = poolHeader + (stringCount + 1) * 4 + oldData.size + enc.size
        val pad = (4 - newPoolSize % 4) % 4
        newPoolSize += pad

        val pool = ByteBuffer.allocate(newPoolSize).order(ByteOrder.LITTLE_ENDIAN)
        pool.put(src, poolOff, poolHeader)                       // purana header
        pool.putInt(4, newPoolSize)                              // size
        pool.putInt(8, stringCount + 1)                          // stringCount
        pool.putInt(20, stringsStart + 4)                        // stringsStart (ek offset zyada)
        pool.position(poolHeader)
        pool.put(src, poolOff + poolHeader, stringCount * 4)     // purane offsets
        pool.putInt(newStringRel)                                // naye string ka offset
        pool.put(oldData)
        pool.put(enc)
        // baaki padding zero

        val rest = src.copyOfRange(oldDataEnd, src.size)
        val out = ByteArray(poolOff + newPoolSize + rest.size)
        System.arraycopy(src, 0, out, 0, poolOff)
        System.arraycopy(pool.array(), 0, out, poolOff, newPoolSize)
        System.arraycopy(rest, 0, out, poolOff + newPoolSize, rest.size)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putInt(4, out.size)

        // Verify: chunks theek chalte hain aur naya string wapas parh hota hai
        val ob = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        var chk = 8
        while (chk + 8 <= out.size) {
            val sz = ob.getInt(chk + 4)
            if (sz <= 0) return null
            chk += sz
        }
        if (chk != out.size) return null
        val so = poolOff + (stringsStart + 4) + ob.getInt(poolOff + poolHeader + stringCount * 4)
        val readBack = if (utf8) {
            var p = so
            p += varLen8(out, p).second
            val (bl, used) = varLen8(out, p); p += used
            String(out, p, bl, Charsets.UTF_8)
        } else {
            val cl = (out[so].toInt() and 0xFF) or ((out[so + 1].toInt() and 0xFF) shl 8)
            String(out, so + 2, cl * 2, Charsets.UTF_16LE)
        }
        if (readBack != newLabel) return null
        return out
    }

    /**
     * resources.arsc fix (v2): sirf ResTable_package chunks (type 0x0200) ke
     * name field (128 UTF-16 chars wali sabit jaga) mein package badlo.
     */
    fun patchResources(bytes: ByteArray, oldPkg: String, newPkg: String): ByteArray {
        if (oldPkg.length != newPkg.length || bytes.size < 12) return bytes
        val out = bytes.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(o: Int) = buf.getShort(o).toInt() and 0xFFFF
        fun u32(o: Int) = buf.getInt(o)
        var off = u16(2) // table header size (aam tor par 12)
        if (off <= 0) off = 12
        while (off + 8 <= out.size) {
            val type = u16(off); val size = u32(off + 4)
            if (size <= 0) break
            if (type == 0x0200 && size >= 268) {
                // name: off+12 se 128 UTF-16 characters
                val sb = StringBuilder()
                var i = 0
                while (i < 128) {
                    val ch = u16(off + 12 + i * 2)
                    if (ch == 0) break
                    sb.append(ch.toChar()); i++
                }
                if (sb.toString() == oldPkg) {
                    val nb = newPkg.toByteArray(Charsets.UTF_16LE)
                    System.arraycopy(nb, 0, out, off + 12, nb.size)
                }
            }
            off += size
        }
        return out
    }

    private fun strAt(pool: StrPool, idx: Int): String? =
        if (idx < 0 || idx >= pool.strings.size) null else pool.strings[idx]

    private fun varLen8(b: ByteArray, p: Int): Pair<Int, Int> {
        val first = b[p].toInt() and 0xFF
        return if (first and 0x80 != 0) Pair(((first and 0x7F) shl 8) or (b[p + 1].toInt() and 0xFF), 2)
        else Pair(first, 1)
    }
}
