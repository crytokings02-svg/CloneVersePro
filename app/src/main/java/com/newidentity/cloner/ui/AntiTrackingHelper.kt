package com.newidentity.cloner.ui

import com.fspacecore.FCore
import com.fspacecore.PackageRule
import java.util.*

/**
 * Advanced Anti-Detection & Spoofing Helper
 * Yeh code apps ko detect karne se rokta hai ki woh clone ya virtual space mein chal rahi hain.
 */
fun applyAntiTrackingAndClone(packageName: String, userId: Int) {
    val random = Random()
    val languages = listOf("en", "fr", "de", "es", "it", "ja")
    val regions = listOf("US", "GB", "DE", "FR", "JP", "IN")
    val timezones = listOf("America/New_York", "Europe/London", "Asia/Tokyo", "Asia/Karachi", "Australia/Sydney")

    // Advanced Hiding Rules
    val rule = PackageRule.Builder(packageName)
        .isHideRoot(true)       // Root access ko poori tarah hide karta hai
        .isHideSim(true)        // SIM card aur carrier tracking ko block karta hai
        .isHideVpn(true)        // VPN detection ko bypass karta hai
        .isHidePath(true)       // Virtual space ke internal storage paths ko chupata hai
        .setLanguage(languages[random.nextInt(languages.size)])
        .setRegion(regions[random.nextInt(regions.size)])
        .setTimeZone(timezones[random.nextInt(timezones.size)])
        .build()

    try {
        // FCore mein rules register karein
        FCore.get().addRule(rule)
        
        // App ko virtual user space mein install karein
        FCore.get().installPackageAsUser(packageName, userId)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
