package com.newidentity.cloner.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.newidentity.cloner.R
import com.newidentity.cloner.engine.CloneStore
import com.newidentity.cloner.model.CloneRecord

/** My Clones v2: Device aur Identity ke saath list. */
class MyClonesActivity : AppCompatActivity() {

    private var clones = mutableListOf<CloneRecord>()
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_clones)
        listView = findViewById(R.id.listClones)
        refresh()
        listView.setOnItemClickListener { _, _, pos, _ -> showActions(clones[pos]) }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        clones = CloneStore.getAll(this)
        listView.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_2, android.R.id.text1,
            clones.map {
                val installed = try { packageManager.getPackageInfo(it.clonedPackage, 0); "🟢 Installed" }
                catch (e: Exception) { "⚪ Not installed" }
                "${it.sourceName} (Clone)  $installed\n${it.identity.device.label} • ${it.clonedPackage}"
            }
        )
        if (clones.isEmpty()) toast("Abhi koi clone nahi banaya. Pehle kisi app ko Clone karo.")
    }

    private fun showActions(rec: CloneRecord) {
        val installed = try { packageManager.getPackageInfo(rec.clonedPackage, 0); true } catch (e: Exception) { false }
        AlertDialog.Builder(this)
            .setTitle("${rec.sourceName} (Clone)")
            .setMessage(
                "Package: ${rec.clonedPackage}\n" +
                "Device: ${rec.identity.device.label} (${rec.identity.device.model})\n" +
                "Android ID: ${rec.identity.androidId}\n" +
                "IMEI: ${rec.identity.imei1}\n" +
                "SIM Info: ${if (rec.identity.hideSimInfo) "Hidden ✔" else "Visible"}\n" +
                "Device Runtime: ${if (rec.runtimeInjected) "Injected ✔" else "Not supported is app mein"}\n" +
                "Installed: ${if (installed) "Haan" else "Nahi"}"
            )
            .setPositiveButton(if (installed) "▶ RUN CLONE" else "INSTALL") { _, _ ->
                if (installed) {
                    packageManager.getLaunchIntentForPackage(rec.clonedPackage)?.let { startActivity(it) }
                        ?: toast("Launch nahi ho saka - app crash ho to mujhe us app ka naam bhejein")
                } else install(rec)
            }
            .setNeutralButton("Identity Dekho") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("New Identity Values")
                    .setMessage(GsonBuilder().setPrettyPrinting().create().toJson(rec.identity))
                    .setPositiveButton("OK", null).show()
            }
            .setNegativeButton("DELETE") { _, _ -> CloneStore.delete(this, rec); refresh() }
            .show()
    }

    private fun install(rec: CloneRecord) {
        if (!CloneFlow.ensureInstallPermission(this)) return
        CloneFlow.installRecord(this, rec) { refresh() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
