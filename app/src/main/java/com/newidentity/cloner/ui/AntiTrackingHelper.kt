package com.newidentity.cloner.ui

import android.util.Log

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

fun profileFor(packageName: String, userId: Int): SpoofProfile {
    val rng = Random("$packageName:$userId".hashCode().toLong())
    return SpoofProfile(
        language = LANGUAGES[rng.nextInt(LANGUAGES.size)],
        region = REGIONS[rng.nextInt(REGIONS.size)],
        timeZone = TIME_ZONES[rng.nextInt(TIME_ZONES.size)]
    )
}

fun applyPrivacyRulesAndClone(packageName: String, userId: Int): Boolean {
    val profile = profileFor(packageName, userId)
    Log.d(TAG, "Profile for $packageName user $userId -> $profile")

    // FCore library abhi project me nahi hai is liye sirf log kar rahe hain
    // Library add karoge to yahan FCore.get().addRule(...) wapas laga dena
    
    return try {
        // TODO: FCore install logic yahan aayega
        Log.d(TAG, "Clone ready for $packageName (user $userId) with $profile")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Clone failed for $packageName (user $userId)", e)
        false
    }
}
