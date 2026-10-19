package com.newidentity.cloner.ui

import com.fspacecore.FCore
import com.fspacecore.PackageRule
import java.util.*

/**
 * Anti-Tracking and Spoofing Helper Function
 */
fun applyAntiTrackingAndClone(packageName: String, userId: Int) {
    val random = Random()
    val languages = listOf("en", "fr", "de", "es", "it", "ja")
    val regions = listOf("US", "GB", "DE", "FR", "JP", "IN")
    val timezones = listOf("America/New_York", "Europe/London", "Asia/Tokyo", "Asia/Karachi", "Australia/Sydney")

    val rule = PackageRule.Builder(packageName)
        .isHideRoot(true)
        .isHideSim(true)
        .isHideVpn(true)
        .isHidePath(true)
        .setLanguage(languages[random.nextInt(languages.size)])
        .setRegion(regions[random.nextInt(regions.size)])
        .setTimeZone(timezones[random.nextInt(timezones.size)])
        .build()

    FCore.get().addRule(rule)
    try {
        FCore.get().installPackageAsUser(packageName, userId)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
