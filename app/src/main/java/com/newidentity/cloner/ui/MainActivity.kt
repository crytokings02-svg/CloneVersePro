package com.newidentity.cloner.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.newidentity.cloner.R
import com.newidentity.cloner.engine.CloneStore
import java.io.File
import kotlin.concurrent.thread

/**
 * Pehli screen (v2): phone ki SAARI launcher apps.
 * v1 ki ghalti: sirf user-apps + getLaunchIntent filter se bohat si apps ghayab theen.
 * Ab Launcher-Intent query + getInstalledApplications dono mila kar list banti hai,
 * aur System apps ka alag switch hai.
 */
class MainActivity : AppCompatActivity() {

    data class Row(val info: ApplicationInfo, val label: String, val meta: String)

    private var all = listOf<Row>()
    private var shown = listOf<Row>()
    private lateinit var adapter: AppAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvAppsCount: TextView
    private var includeSystem = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAppsCount = findViewById(R.id.tvAppsCount)
        val list = findViewById<ListView>(R.id.listApps)
        adapter = AppAdapter(); list.adapter = adapter

        findViewById<View>(R.id.cardMyClones).setOnClickListener {
            startActivity(Intent(this, MyClonesActivity::class.java))
        }
        findViewById<Switch>(R.id.swSystemApps).setOnCheckedChangeListener { _, checked ->
            includeSystem = checked; loadApps()
        }
        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        // Tap = seedha Clone + Install (default random identity). Lamba dabao = Advanced Settings.
        list.setOnItemClickListener { _, _, pos, _ ->
            val row = shown[pos]
            AlertDialog.Builder(this)
                .setTitle("${row.label} clone karein?")
                .setMessage("Nayi random device identity ke saath clone banega aur seedha install hoga.")
                .setPositiveButton("CLONE + INSTALL") { _, _ -> CloneFlow.quickClone(this, row.info, row.label) }
                .setNeutralButton("Settings") { _, _ -> openSettings(row.info.packageName) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            openSettings(shown[pos].info.packageName); true
        }
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvClonesCount).text = CloneStore.getAll(this).size.toString()
    }

    private fun openSettings(pkg: String) {
        startActivity(Intent(this, CloneSettingsActivity::class.java).putExtra("pkg", pkg))
    }

    private fun loadApps() {
        tvStatus.text = "Apps load ho rahi hain..."
        val system = includeSystem
        thread {
            val pm = packageManager
            val byPkg = LinkedHashMap<String, ApplicationInfo>()

            // 1) Launcher par nazar anay wali apps
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                    val ai = ri.activityInfo?.applicationInfo ?: continue
                    byPkg[ai.packageName] = ai
                }
            } catch (e: Exception) { }

            // 2) Baaki installed apps bhi mila lo
            try {
                for (ai in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                    if (!byPkg.containsKey(ai.packageName) && pm.getLaunchIntentForPackage(ai.packageName) != null)
                        byPkg[ai.packageName] = ai
                }
            } catch (e: Exception) { }

            val rows = byPkg.values
                .filter { it.packageName != packageName }
                .filter { system || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
                .map { ai ->
                    val label = ai.loadLabel(pm).toString()
                    val sizeMb = try { File(ai.sourceDir).length() / (1024.0 * 1024) } catch (e: Exception) { 0.0 }
                    val split = if (!ai.splitSourceDirs.isNullOrEmpty()) " • Split APK" else ""
                    Row(ai, label, String.format("%.1f MB%s", sizeMb, split))
                }
                .sortedBy { it.label.lowercase() }

            runOnUiThread {
                all = rows; shown = rows
                tvAppsCount.text = rows.size.toString()
                tvStatus.text = if (rows.isEmpty()) "Koi app nahi mili!"
                else "Clone karne ke liye app par tap karo (lamba dabao = settings):"
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun filter(q: String) {
        val query = q.trim().lowercase()
        shown = if (query.isEmpty()) all else all.filter {
            it.label.lowercase().contains(query) || it.info.packageName.lowercase().contains(query)
        }
        adapter.notifyDataSetChanged()
    }

    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(pos: Int) = shown[pos]
        override fun getItemId(pos: Int) = pos.toLong()
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val row = shown[pos]
            v.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(row.info.loadIcon(packageManager))
            v.findViewById<TextView>(R.id.tvName).text = row.label
            v.findViewById<TextView>(R.id.tvPkg).text = row.info.packageName
            v.findViewById<TextView>(R.id.tvMeta).text = row.meta
            return v
        }
    }
}
