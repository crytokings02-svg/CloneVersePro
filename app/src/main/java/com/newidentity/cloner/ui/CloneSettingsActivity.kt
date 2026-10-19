package com.newidentity.cloner.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.newidentity.cloner.R
import com.newidentity.cloner.engine.CloneEngine
import com.newidentity.cloner.engine.CloneStore
import com.newidentity.cloner.engine.DeviceProfiles
import com.newidentity.cloner.engine.IdentityGenerator
import com.newidentity.cloner.model.CloneIdentity
import com.newidentity.cloner.model.CloneOptions
import com.newidentity.cloner.model.CloneRecord
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** Clone Settings v2: Virtual Device, Multiple Clones, SIM sirf Hide ON. */
class CloneSettingsActivity : AppCompatActivity() {

    private lateinit var appInfo: ApplicationInfo
    private lateinit var appName: String
    private var identity = IdentityGenerator.newIdentity()
    private var cloneCount = 1

    private fun et(id: Int) = findViewById<EditText>(id)
    private fun sw(id: Int) = findViewById<Switch>(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clone_settings)

        val pkg = intent.getStringExtra("pkg") ?: return finish()
        appInfo = packageManager.getApplicationInfo(pkg, 0)
        appName = appInfo.loadLabel(packageManager).toString()

        findViewById<ImageView>(R.id.ivIcon).setImageDrawable(appInfo.loadIcon(packageManager))
        findViewById<TextView>(R.id.tvAppName).text = appName
        findViewById<TextView>(R.id.tvPkg).text = pkg

        val spinner = findViewById<Spinner>(R.id.spinnerDevice)
        val labels = listOf("🎲 Random Device (har dafa alag)") + DeviceProfiles.presets.map { it.label }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                identity.device = if (pos == 0) DeviceProfiles.randomInstance()
                else DeviceProfiles.newInstance(DeviceProfiles.presets[pos - 1])
                identity.serial = identity.device.runtimeSerial
                updateDeviceTexts()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnMinus).setOnClickListener { setCount(cloneCount - 1) }
        findViewById<Button>(R.id.btnPlus).setOnClickListener { setCount(cloneCount + 1) }

        findViewById<Button>(R.id.btnNewIdentity).setOnClickListener {
            val pos = spinner.selectedItemPosition
            val dev = if (pos == 0) DeviceProfiles.randomInstance()
            else DeviceProfiles.newInstance(DeviceProfiles.presets[pos - 1])
            identity = IdentityGenerator.newIdentity(dev)
            fillFields(); updateDeviceTexts()
            toast("Nayi Identity + ${dev.label} tayyar!")
        }

        fillFields(); updateDeviceTexts()
        findViewById<Button>(R.id.btnClone).setOnClickListener { startCloning() }
    }

    private fun setCount(n: Int) {
        cloneCount = n.coerceIn(1, 5)
        findViewById<TextView>(R.id.tvCount).text = cloneCount.toString()
    }

    private fun updateDeviceTexts() {
        val d = identity.device
        findViewById<TextView>(R.id.tvDeviceNow).text = d.label
        findViewById<TextView>(R.id.tvDeviceDetail).text =
            "${d.manufacturer} • ${d.model} • Android ${d.androidRelease} • Serial ${d.runtimeSerial}"
    }

    private fun fillFields() {
        et(R.id.etAndroidId).setText(identity.androidId)
        et(R.id.etImei1).setText(identity.imei1); et(R.id.etImei2).setText(identity.imei2)
        et(R.id.etImsi).setText(identity.imsi)
        et(R.id.etWifiMac).setText(identity.wifiMac); et(R.id.etBluetoothMac).setText(identity.bluetoothMac)
        et(R.id.etAdId).setText(identity.advertisingId); et(R.id.etGsfId).setText(identity.gsfId)
    }

    private fun collectIdentity(devOverride: com.newidentity.cloner.model.DeviceInstance?): CloneIdentity {
        val dev = devOverride ?: identity.device
        return CloneIdentity(
            androidIdEnabled = sw(R.id.swAndroidId).isChecked,
            androidId = et(R.id.etAndroidId).text.toString().trim(),
            imeiImsiEnabled = sw(R.id.swImei).isChecked,
            imei1 = et(R.id.etImei1).text.toString().trim(),
            imei2 = et(R.id.etImei2).text.toString().trim(),
            imsi = et(R.id.etImsi).text.toString().trim(),
            wifiMacEnabled = sw(R.id.swWifiMac).isChecked,
            wifiMac = et(R.id.etWifiMac).text.toString().trim(),
            BluetoothMacEnabled = sw(R.id.swWifiMac).isChecked,
            bluetoothMac = et(R.id.etBluetoothMac).text.toString().trim(),
            adIdEnabled = sw(R.id.swAdId).isChecked,
            advertisingId = et(R.id.etAdId).text.toString().trim(),
            gsfId = et(R.id.etGsfId).text.toString().trim(),
            hideSimInfo = sw(R.id.swSim).isChecked,
            device = dev,
            serial = dev.runtimeSerial
        )
    }

    private fun startCloning() {
        if (!CloneFlow.ensureInstallPermission(this)) return
        val hideSim = sw(R.id.swSim).isChecked
        val useRuntime = sw(R.id.swRuntime).isChecked
        val stripAd = sw(R.id.swAdId).isChecked
        val randomEach = sw(R.id.swRandomDeviceEach).isChecked
        val total = cloneCount
        val firstIdentity = collectIdentity(null)

        @Suppress("DEPRECATION")
        val dialog = ProgressDialog(this).apply {
            setTitle("Clone ban raha hai..."); setMessage("Tayyari..."); setCancelable(false); show()
        }

        thread {
            val made = mutableListOf<CloneRecord>()
            var lastWarning = ""
            try {
                for (i in 1..total) {
                    val idForThis = if (i == 1) firstIdentity
                    else IdentityGenerator.newIdentity(
                        if (randomEach) DeviceProfiles.randomInstance()
                        else DeviceProfiles.newInstance(
                            DeviceProfiles.presets.firstOrNull { it.label == firstIdentity.device.label }
                                ?: DeviceProfiles.randomPreset()
                        )
                    ).also { it.hideSimInfo = hideSim }

                    val number = CloneStore.nextCloneNumber(this, appInfo.packageName)
                    val options = CloneOptions(
                        sourcePackage = appInfo.packageName, sourceName = appName,
                        cloneNumber = number,
                        stripPhonePermissions = hideSim,
                        stripAdIdPermission = stripAd,
                        stripLocationPermission = hideSim,
                        useDeviceRuntime = useRuntime,
                        identity = idForThis
                    )
                    val result = CloneEngine.cloneApp(this, appInfo, options) { stage ->
                        runOnUiThread { dialog.setMessage("Clone $i / $total\n$stage") }
                    }
                    val rec = CloneRecord(
                        id = UUID.randomUUID().toString(),
                        sourcePackage = appInfo.packageName, sourceName = appName,
                        clonedPackage = result.clonedPackage, apkPath = result.apkFile.absolutePath,
                        apkPaths = result.apkFiles.map { it.absolutePath },
                        identity = idForThis, runtimeInjected = result.runtimeInjected
                    )
                    CloneStore.add(this, rec); made.add(rec); lastWarning = result.warnings
                }
                runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle(if (made.size > 1) "${made.size} Clone Tayyar!" else "Clone Tayyar!")
                        .setMessage(
                            made.joinToString("\n") { "• ${it.clonedPackage}\n   Device: ${it.identity.device.label}" } +
                            (if (lastWarning.isNotBlank()) "\n\n$lastWarning" else "") +
                            "\n\nINSTALL dabao — install ke baad RUN CLONE ka option ayega."
                        )
                        .setPositiveButton("INSTALL CLONE") { _, _ -> installChain(made, 0) }
                        .setNeutralButton("My Clones") { _, _ -> startActivity(Intent(this, MyClonesActivity::class.java)) }
                        .setNegativeButton("Baad Mein", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(this)
                        .setTitle("Clone Fail Ho Gaya")
                        .setMessage("${e.javaClass.simpleName}: ${e.message}\n\nYeh app shayad Split APK / signature-protected hai. Pehle koi chhoti app (Gallery, Calculator) try karein.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    /** Saare bane hue clones ek ek karke install karo. */
    private fun installChain(list: List<CloneRecord>, i: Int) {
        if (i >= list.size) return
        val rec = list[i]
        CloneFlow.installRecord(this, rec, askRun = i == list.lastIndex) { installChain(list, i + 1) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
