package com.newidentity.cloner.engine

import com.newidentity.cloner.model.DeviceInstance
import com.newidentity.cloner.model.DevicePreset
import kotlin.random.Random

/** Multiple Virtual Devices - har clone ko alag device mil sakta hai. */
object DeviceProfiles {

    val presets: List<DevicePreset> = listOf(
        DevicePreset("Samsung Galaxy S24 Ultra", "samsung", "samsung", "SM-S928B", "e3q", "e3qxxx", "qcom", "14", "UP1A.231005.007"),
        DevicePreset("Samsung Galaxy A55", "samsung", "samsung", "SM-A556B", "a55x", "a55xnsxx", "exynos1480", "14", "UP1A.231005.007"),
        DevicePreset("Google Pixel 8 Pro", "google", "Google", "Pixel 8 Pro", "husky", "husky", "husky", "14", "AP1A.240405.002"),
        DevicePreset("Google Pixel 7a", "google", "Google", "Pixel 7a", "lynx", "lynx", "lynx", "14", "AP1A.240405.002"),
        DevicePreset("OnePlus 12", "OnePlus", "OnePlus", "CPH2573", "OP595DL1", "CPH2573", "qcom", "14", "UKQ1.230924.001"),
        DevicePreset("Xiaomi 14", "Xiaomi", "Xiaomi", "23127PN0CG", "houji", "houji_global", "qcom", "14", "UKQ1.230804.001"),
        DevicePreset("Redmi Note 13", "Redmi", "Xiaomi", "23129RA7EO", "sapphire", "sapphire_global", "mt6768", "13", "TP1A.220624.014"),
        DevicePreset("Realme 12 Pro", "realme", "realme", "RMX3842", "RE5C4FL1", "RMX3842", "qcom", "14", "UKQ1.230924.001"),
        DevicePreset("Vivo V30", "vivo", "vivo", "V2318", "V2318", "V2318", "qcom", "14", "UP1A.231005.007"),
        DevicePreset("Oppo Reno 11", "OPPO", "OPPO", "CPH2599", "OP5A29L1", "CPH2599", "mt6877", "14", "UP1A.231005.007"),
        DevicePreset("Infinix Note 40", "Infinix", "Infinix", "Infinix X6853", "Infinix-X6853", "X6853-OP", "mt6789", "14", "UP1A.231005.007"),
        DevicePreset("Tecno Camon 30", "TECNO", "TECNO", "TECNO CL6", "TECNO-CL6", "CL6-OP", "mt6789", "14", "UP1A.231005.007")
    )

    fun randomPreset(): DevicePreset = presets.random()

    fun newInstance(preset: DevicePreset): DeviceInstance {
        val serial = randomSerial()
        val incremental = (100000 + Random.nextInt(899999)).toString()
        return preset.instantiate(serial, incremental)
    }

    fun randomInstance(): DeviceInstance = newInstance(randomPreset())

    private fun randomSerial(): String {
        val c = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..11).map { c[Random.nextInt(c.length)] }.joinToString("")
    }
}
