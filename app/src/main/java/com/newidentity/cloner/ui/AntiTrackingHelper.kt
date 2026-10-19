package com.newidentity.cloner.ui

import android.util.Log
import com.fspacecore.FCore
import com.fspacecore.PackageRule
import java.util.Random

private const val TAG = "CloneHelper"

/** Locale/timezone profile applied to a cloned app instance. */
data class SpoofProfile(
    val language: String,
    val region: String,
    val timeZone: String
)

private val LANGUAGES = listOf("en", "fr", "de", "es", "it", "ja")
private val REGIONS = listOf("US", "GB", "DE", "FR", "JP", "IN")
private val TIME_ZONES = listOf(
    "America/New_York", "Europe/London", "Asia/Tokyo",
    "Asia/Karachi", "Australia/Sydney"
)

/**
 * Builds a profile that is stable for a given (package, user) pair,
 * so the cloned app always sees the same locale across launches.
 */
fun profileFor(packageName: String, userId: Int): SpoofProfile {
    val rng = Random("$packageName:$userId".hashCode().toLong())
    return SpoofProfile(
        language = LANGUAGES[rng.nextInt(LANGUAGES.size)],
        region = REGIONS[rng.nextInt(REGIONS.size)],
        timeZone = TIME_ZONES[rng.nextInt(TIME_ZONES.size)]
    )
}

/**
 * Registers privacy rules for [packageName] and installs it
 * into the virtual user space [userId].
 *
 * @return true on success, false if registration or install failed.
 */
fun applyPrivacyRulesAndClone(packageName: String, userId: Int): Boolean {
    val profile = profileFor(packageName, userId)

    val rule = PackageRule.Builder(packageName)
        .isHideRoot(true)
        .isHideSim(true)
        .isHideVpn(true)
        .isHidePath(true)
        .setLanguage(profile.language)
        .setRegion(profile.region)
        .setTimeZone(profile.timeZone)
        .build()

    return try {
        FCore.get().addRule(rule)
        FCore.get().installPackageAsUser(packageName, userId)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Clone failed for $packageName (user $userId)", e)
        false
    }
}
