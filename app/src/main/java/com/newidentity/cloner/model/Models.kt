package com.newidentity.cloner.model

/** Ek Virtual Device (masalan Samsung Galaxy S24) jo clone ko diya jayega. */
data class DevicePreset(
    val label: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val androidRelease: String,
    val buildId: String
) {
    /** Is preset ko is clone ke liye random serial/fingerprint ke saath tayyar karna */
    fun instantiate(serial: String, incremental: String): DeviceInstance {
        val fp = "$brand/$product/$device:$androidRelease/$buildId/$incremental:user/release-keys"
        return DeviceInstance(
            label = label, brand = brand, manufacturer = manufacturer, model = model,
            device = device, product = product, hardware = hardware,
            androidRelease = androidRelease, buildId = buildId,
            fingerprint = fp, runtimeSerial = serial
        )
    }
}

data class DeviceInstance(
    val label: String = "",
    val brand: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val product: String = "",
    val hardware: String = "",
    val androidRelease: String = "",
    val buildId: String = "",
    val fingerprint: String = "",
    val runtimeSerial: String = ""
)

/** Ek Clone ki nayi pehchan. SIM hissa sirf HIDE hai - koi carrier select nahi hota. */
data class CloneIdentity(
    var androidIdEnabled: Boolean = true,
    var androidId: String = "",
    var imeiImsiEnabled: Boolean = true,
    var imei1: String = "",
    var imei2: String = "",
    var imsi: String = "",
    var wifiMacEnabled: Boolean = true,
    var wifiMac: String = "",
    var BluetoothMacEnabled: Boolean = true,
    var bluetoothMac: String = "",
    var adIdEnabled: Boolean = true,
    var advertisingId: String = "",
    var gsfId: String = "",
    /** Sirf ON/OFF - local SIM/number/location/carrier sab hide */
    var hideSimInfo: Boolean = true,
    var device: DeviceInstance = DeviceInstance(),
    var serial: String = "",
    var createdAt: Long = System.currentTimeMillis()
)

data class CloneOptions(
    val sourcePackage: String,
    val sourceName: String,
    val cloneNumber: Int = 1,
    val stripPhonePermissions: Boolean = true,
    val stripAdIdPermission: Boolean = true,
    val stripLocationPermission: Boolean = true,
    val useDeviceRuntime: Boolean = true,
    val identity: CloneIdentity = CloneIdentity()
)

data class CloneRecord(
    val id: String,
    val sourcePackage: String,
    val sourceName: String,
    val clonedPackage: String,
    val apkPath: String,
    /** base + split APK files (purane records mein null ho sakta hai) */
    val apkPaths: List<String>? = null,
    val identity: CloneIdentity,
    /** Runtime factory inject hua tha ya nahi */
    val runtimeInjected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun allApkPaths(): List<String> = apkPaths?.takeIf { it.isNotEmpty() } ?: listOf(apkPath)
}
