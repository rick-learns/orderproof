package dev.orderproof.verifier.device

import kotlinx.serialization.Serializable

/** What the scenarios ran on, as reported by the device itself. */
@Serializable
data class DeviceProfile(
    val serial: String,
    val avdName: String,
    val model: String,
    val apiLevel: Int,
    val androidRelease: String,
    val abi: String,
    val screenSize: String,
    val density: String,
    val fingerprint: String,
    val emulator: Boolean,
) {
    companion object {
        fun read(adb: Adb): DeviceProfile {
            fun prop(name: String) = adb.shell("getprop $name")
            return DeviceProfile(
                serial = adb.serial,
                avdName = prop("ro.boot.qemu.avd_name").ifEmpty { prop("ro.kernel.qemu.avd_name") }.ifEmpty { "unknown" },
                model = prop("ro.product.model"),
                apiLevel = prop("ro.build.version.sdk").toIntOrNull() ?: -1,
                androidRelease = prop("ro.build.version.release"),
                abi = prop("ro.product.cpu.abi"),
                screenSize = adb.shell("wm size").lines().last().substringAfter(": ").trim(),
                density = adb.shell("wm density").lines().last().substringAfter(": ").trim(),
                fingerprint = prop("ro.build.fingerprint"),
                emulator = prop("ro.kernel.qemu") == "1" || prop("ro.boot.qemu") == "1",
            )
        }
    }
}
