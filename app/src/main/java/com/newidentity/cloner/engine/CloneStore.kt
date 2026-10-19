package com.newidentity.cloner.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.newidentity.cloner.model.CloneRecord

/** Banaye hue clones ki list save karna */
object CloneStore {
    private val gson = Gson()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("my_clones", Context.MODE_PRIVATE)

    fun getAll(ctx: Context): MutableList<CloneRecord> {
        val json = prefs(ctx).getString("list", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<CloneRecord>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun add(ctx: Context, rec: CloneRecord) {
        val list = getAll(ctx); list.add(0, rec)
        prefs(ctx).edit().putString("list", gson.toJson(list)).apply()
    }

    fun delete(ctx: Context, rec: CloneRecord) {
        val list = getAll(ctx); list.removeAll { it.id == rec.id }
        prefs(ctx).edit().putString("list", gson.toJson(list)).apply()
        try {
            rec.allApkPaths().forEach { java.io.File(it).delete() }
            java.io.File(rec.apkPath).parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
        } catch (e: Exception) {}
    }

    fun nextCloneNumber(ctx: Context, sourcePackage: String): Int =
        getAll(ctx).count { it.sourcePackage == sourcePackage } + 1
}
