package com.newidentity.cloner.engine

import com.newidentity.cloner.model.CloneIdentity
import com.newidentity.cloner.model.DeviceInstance
import java.util.UUID
import kotlin.random.Random

/**
 * One Click -> saari nayi random identity.
 * NOTE: SIM/Carrier selection khatm kar diya gaya hai - local SIM sirf HIDE hoti hai.
 * IMSI test-network (MCC 001) par banta hai, kisi asal carrier ka naam nahi ata.
 */
object IdentityGenerator {

    fun newIdentity(device: DeviceInstance = DeviceProfiles.randomInstance()): CloneIdentity = CloneIdentity(
        androidId = randomHex(16),
        imei1 = generateImei(),
        imei2 = generateImei(),
        imsi = "00101" + randomDigits(10), // 001 = test network
        wifiMac = generateMac(),
        bluetoothMac = generateMac(),
        advertisingId = UUID.randomUUID().toString(),
        gsfId = randomHex(16),
        hideSimInfo = true,
        device = device,
        serial = device.runtimeSerial
    )

    fun randomHex(n: Int) = (1..n).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
    fun randomDigits(n: Int) = (1..n).map { Random.nextInt(10) }.joinToString("")
    fun generateMac(): String {
        val first = listOf("02", "06", "0A", "0E", "12", "1A", "2E", "3A", "4E", "5A", "6E", "7A").random()
        return (listOf(first) + (1..5).map { "%02X".format(Random.nextInt(256)) }).joinToString(":")
    }
    fun generateImei(): String {
        val base = "35" + randomDigits(12)
        return base + luhn(base)
    }
    private fun luhn(number: String): Int {
        var sum = 0; var alt = true
        for (i in number.length - 1 downTo 0) {
            var n = number[i].digitToInt()
            if (alt) { n *= 2; if (n > 9) n -= 9 }
            sum += n; alt = !alt
        }
        return (10 - sum % 10) % 10
    }
}
