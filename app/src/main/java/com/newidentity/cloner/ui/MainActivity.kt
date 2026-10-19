package com.newidentity.cloner.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.newidentity.cloner.R
import com.newidentity.cloner.engine.CloneStore
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

/** Lists installed apps and lets the user clone one into the virtual space. */
class MainActivity : AppCompatActivity() {

    private data class Row(val info: ApplicationInfo, val label: String, val meta: String)

    private var allApps = listOf<Row>()
    private var shownApps = listOf<Row>()
    private var includeSystem = false
    private var loadGeneration = 0

    private lateinit var adapter: AppAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvAppsCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAppsCount = findViewById(R.id.tvAppsCount)

        adapter = AppAdapter()
        val list = findViewById<ListView>(R.id.listApps)
        list.adapter = adapter

        findViewById<View>(R.id.cardMyClones).setOnClickListener {
            startActivity(Intent(this, MyClonesActivity::class.java))
        }

        findViewById<CompoundButton>(R.id.swSystemApps).setOnCheckedChangeListener { _, checked ->
            includeSystem = checked
            loadApps()
        }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        list.setOnItemClickListener { _, _, pos, _ -> showCloneDialog(shownApps[pos]) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            openSettings(shownApps[pos].info.packageName)
            true
        }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvClonesCount).text =
            CloneStore.getAll(this).size.toString()
    }

    private fun showCloneDialog(row: Row) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clone_dialog_title, row.label))
            .setMessage(R.string.clone_dialog_message)
            .setPositiveButton(R.string.action_clone_install) { _, _ -> cloneApp(row) }
            .setNeutralButton(R.string.action_settings) { _, _ -> openSettings(row.info.packageName) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cloneApp(row: Row) {
        thread {
            val ok = applyPrivacyRulesAndClone(row.info.packageName, DEFAULT_USER_ID)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (ok) {
                    CloneFlow.quickClone(this, row.info, row.label)
                } else {
                    Toast.makeText(this, R.string.clone_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openSettings(pkg: String) {
        startActivity(Intent(this, CloneSettingsActivity::class.java).putExtra("pkg", pkg))
    }

    private fun loadApps() {
        tvStatus.setText(R.string.status_loading)
        val system = includeSystem
        val generation = ++loadGeneration

        thread {
            val pm = packageManager
            val byPkg = LinkedHashMap<String, ApplicationInfo>()

            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                    val ai = ri.activityInfo?.applicationInfo ?: continue
                    byPkg[ai.packageName] = ai
                }
            } catch (e: Exception) {
                Log.w(TAG, "Launcher query failed", e)
            }

            try {
                for (ai in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                    if (ai.packageName !in byPkg && pm.getLaunchIntentForPackage(ai.packageName) != null) {
                        byPkg[ai.packageName] = ai
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Installed apps query failed", e)
            }

            val rows = byPkg.values
                .filter { it.packageName != packageName }
                .filter { system || !it.isSystemOnly() }
                .map { ai ->
                    val sizeMb = try {
                        File(ai.sourceDir).length() / (1024.0 * 1024)
                    } catch (e: Exception) {
                        0.0
                    }
                    val split = if (!ai.splitSourceDirs.isNullOrEmpty()) " • Split APK" else ""
                    Row(ai, ai.loadLabel(pm).toString(), String.format(Locale.US, "%.1f MB%s", sizeMb, split))
                }
                .sortedBy { it.label.lowercase() }

            runOnUiThread {
                // Ignore stale results or results arriving after the Activity is gone.
                if (isFinishing || isDestroyed || generation != loadGeneration) return@runOnUiThread
                allApps = rows
                shownApps = rows
                tvAppsCount.text = rows.size.toString()
                tvStatus.setText(if (rows.isEmpty()) R.string.status_no_apps else R.string.status_hint)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun ApplicationInfo.isSystemOnly(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        shownApps = if (query.isEmpty()) allApps else allApps.filter {
            it.label.lowercase().contains(query) || it.info.packageName.lowercase().contains(query)
        }
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shownApps.size
        override fun getItem(pos: Int) = shownApps[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val h = (v.tag as? Holder) ?: Holder(v).also { v.tag = it }
            val row = shownApps[pos]
            h.icon.setImageDrawable(row.info.loadIcon(packageManager))
            h.name.text = row.label
            h.pkg.text = row.info.packageName
            h.meta.text = row.meta
            return v
        }
    }

    private class Holder(v: View) {
        val icon: ImageView = v.findViewById(R.id.ivIcon)
        val name: TextView = v.findViewById(R.id.tvName)
        val pkg: TextView = v.findViewById(R.id.tvPkg)
        val meta: TextView = v.findViewById(R.id.tvMeta)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val DEFAULT_USER_ID = 1
    }
}
