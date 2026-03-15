package vegabobo.dsusideloader.porting.device

import android.os.Build
import android.util.Log
import vegabobo.dsusideloader.porting.SystemToolsManager
import vegabobo.dsusideloader.util.DevicePropUtils

/**
 * Detects device information and matches it with appropriate device profiles
 */
class DeviceDetector(
    private val systemToolsManager: SystemToolsManager
) {
    
    private val tag = "DeviceDetector"
    
    data class DeviceInfo(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val device: String,
        val codename: String,
        val architecture: String,
        val androidVersion: Int,
        val buildFingerprint: String,
        val partitionLayout: String,
        val bootloaderVersion: String?,
        val kernelVersion: String,
        val selinuxStatus: String,
        val properties: Map<String, String>
    )
    
    /**
     * Detect current device information
     */
    suspend fun detectDevice(): DeviceInfo {
        Log.d(tag, "Detecting device information...")
        
        val properties = getSystemProperties()
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            codename = properties["ro.product.device"] ?: Build.DEVICE,
            architecture = detectArchitecture(),
            androidVersion = Build.VERSION.SDK_INT,
            buildFingerprint = Build.FINGERPRINT,
            partitionLayout = detectPartitionLayout(),
            bootloaderVersion = properties["ro.bootloader"],
            kernelVersion = detectKernelVersion(),
            selinuxStatus = detectSelinuxStatus(),
            properties = properties
        )
    }
    
    /**
     * Get system properties relevant to ROM porting
     */
    private suspend fun getSystemProperties(): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        
        val importantProps = listOf(
            "ro.product.manufacturer",
            "ro.product.brand", 
            "ro.product.model",
            "ro.product.device",
            "ro.product.name",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.build.fingerprint",
            "ro.bootloader",
            "ro.hardware",
            "ro.hardware.chipset",
            "ro.board.platform",
            "ro.product.cpu.abi",
            "ro.product.cpu.abilist",
            "ro.product.cpu.abilist32",
            "ro.product.cpu.abilist64",
            "ro.build.ab_update",
            "ro.boot.dynamic_partitions",
            "ro.boot.slot_suffix",
            "ro.treble.enabled",
            "ro.vndk.version"
        )
        
        for (prop in importantProps) {
            try {
                val result = systemToolsManager.executeCommand("getprop $prop")
                if (result.success && result.output.isNotBlank()) {
                    properties[prop] = result.output.trim()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to get property $prop: ${e.message}")
            }
        }
        
        return properties
    }
    
    /**
     * Detect device architecture
     */
    private fun detectArchitecture(): String {
        return when {
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() -> {
                when (Build.SUPPORTED_64_BIT_ABIS[0]) {
                    "arm64-v8a" -> "arm64-v8a"
                    "x86_64" -> "x86_64"
                    else -> "arm64-v8a" // Default to arm64
                }
            }
            Build.SUPPORTED_32_BIT_ABIS.isNotEmpty() -> {
                when (Build.SUPPORTED_32_BIT_ABIS[0]) {
                    "armeabi-v7a" -> "armeabi-v7a"
                    "x86" -> "x86"
                    else -> "armeabi-v7a" // Default to arm32
                }
            }
            else -> "arm64-v8a" // Fallback
        }
    }
    
    /**
     * Detect partition layout (A-only vs A/B)
     */
    private suspend fun detectPartitionLayout(): String {
        // Check for A/B update property
        val abUpdateResult = systemToolsManager.executeCommand("getprop ro.build.ab_update")
        if (abUpdateResult.success && abUpdateResult.output.trim() == "true") {
            return "A/B"
        }
        
        // Check for slot suffix
        val slotSuffixResult = systemToolsManager.executeCommand("getprop ro.boot.slot_suffix")
        if (slotSuffixResult.success && slotSuffixResult.output.trim().isNotEmpty()) {
            return "A/B"
        }
        
        // Check for dynamic partitions
        val dynamicPartitionsResult = systemToolsManager.executeCommand("getprop ro.boot.dynamic_partitions")
        if (dynamicPartitionsResult.success && dynamicPartitionsResult.output.trim() == "true") {
            return "A/B-Dynamic"
        }
        
        // Check if system_a partition exists
        val systemAResult = systemToolsManager.executeCommand("ls /dev/block/by-name/system_a")
        if (systemAResult.success) {
            return "A/B"
        }
        
        return "A-only"
    }
    
    /**
     * Detect kernel version
     */
    private suspend fun detectKernelVersion(): String {
        val result = systemToolsManager.executeCommand("uname -r")
        return if (result.success) {
            result.output.trim()
        } else {
            "Unknown"
        }
    }
    
    /**
     * Detect SELinux status
     */
    private suspend fun detectSelinuxStatus(): String {
        val result = systemToolsManager.executeCommand("getenforce")
        return if (result.success) {
            result.output.trim()
        } else {
            "Unknown"
        }
    }
    
    /**
     * Find matching device profile for the detected device
     */
    fun findMatchingProfile(deviceInfo: DeviceInfo): DeviceProfile? {
        Log.d(tag, "Finding matching profile for ${deviceInfo.manufacturer} ${deviceInfo.model}")
        
        // Check for exact device match first
        val exactMatch = findExactDeviceMatch(deviceInfo)
        if (exactMatch != null) {
            Log.d(tag, "Found exact device match: ${exactMatch.deviceName}")
            return exactMatch
        }
        
        // Check for manufacturer-specific profile
        val manufacturerMatch = findManufacturerMatch(deviceInfo)
        if (manufacturerMatch != null) {
            Log.d(tag, "Found manufacturer match: ${manufacturerMatch.deviceName}")
            return manufacturerMatch
        }
        
        // Fall back to generic profile
        Log.d(tag, "Using generic profile for ${deviceInfo.architecture}")
        return getGenericProfile(deviceInfo)
    }
    
    /**
     * Find exact device match in predefined profiles
     */
    private fun findExactDeviceMatch(deviceInfo: DeviceInfo): DeviceProfile? {
        // This would be expanded with a database of device profiles
        // For now, we'll check against known devices
        
        return when {
            deviceInfo.manufacturer.lowercase() == "samsung" -> {
                createSamsungProfileForDevice(deviceInfo)
            }
            else -> null
        }
    }
    
    /**
     * Find manufacturer-specific profile
     */
    private fun findManufacturerMatch(deviceInfo: DeviceInfo): DeviceProfile? {
        return when (deviceInfo.manufacturer.lowercase()) {
            "samsung" -> createGenericSamsungProfile(deviceInfo)
            "google" -> createGenericPixelProfile(deviceInfo)
            "xiaomi" -> createGenericXiaomiProfile(deviceInfo)
            else -> null
        }
    }
    
    /**
     * Get generic profile based on architecture
     */
    private fun getGenericProfile(deviceInfo: DeviceInfo): DeviceProfile {
        return when (deviceInfo.architecture) {
            "arm64-v8a" -> DeviceProfiles.GENERIC_ARM64.copy(
                deviceName = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                codename = deviceInfo.codename,
                manufacturer = deviceInfo.manufacturer,
                minAndroidVersion = deviceInfo.androidVersion,
                supportedPartitionLayouts = listOf(deviceInfo.partitionLayout)
            )
            else -> DeviceProfiles.GENERIC_ARM64 // Fallback
        }
    }
    
    /**
     * Create Samsung-specific profile for detected device
     */
    private fun createSamsungProfileForDevice(deviceInfo: DeviceInfo): DeviceProfile? {
        // Estimate partition sizes based on device model
        val (systemSize, vendorSize) = estimateSamsungPartitionSizes(deviceInfo.model)
        
        return DeviceProfiles.createSamsungProfile(
            deviceName = "${deviceInfo.manufacturer} ${deviceInfo.model}",
            codename = deviceInfo.codename,
            androidVersion = deviceInfo.androidVersion,
            systemSize = systemSize,
            vendorSize = vendorSize
        ).copy(
            supportedPartitionLayouts = listOf(deviceInfo.partitionLayout)
        )
    }
    
    /**
     * Create generic Samsung profile
     */
    private fun createGenericSamsungProfile(deviceInfo: DeviceInfo): DeviceProfile {
        return DeviceProfiles.createSamsungProfile(
            deviceName = "${deviceInfo.manufacturer} ${deviceInfo.model}",
            codename = deviceInfo.codename,
            androidVersion = deviceInfo.androidVersion,
            systemSize = 4L * 1024 * 1024 * 1024, // 4GB default
            vendorSize = 1L * 1024 * 1024 * 1024   // 1GB default
        )
    }
    
    /**
     * Create generic Pixel profile
     */
    private fun createGenericPixelProfile(deviceInfo: DeviceInfo): DeviceProfile {
        return DeviceProfile(
            deviceName = "${deviceInfo.manufacturer} ${deviceInfo.model}",
            codename = deviceInfo.codename,
            manufacturer = deviceInfo.manufacturer,
            architecture = deviceInfo.architecture,
            minAndroidVersion = deviceInfo.androidVersion,
            maxAndroidVersion = null,
            supportedPartitionLayouts = listOf(deviceInfo.partitionLayout),
            vendorOverlays = emptyList(),
            kernelConfig = null,
            bootloaderConfig = BootloaderConfig(
                type = "fastboot",
                isUnlocked = true,
                verificationDisabled = true,
                customRecoverySupported = true,
                flashingMethod = "fastboot"
            ),
            partitionSizes = PartitionSizes(
                systemSize = 3L * 1024 * 1024 * 1024,
                vendorSize = 1L * 1024 * 1024 * 1024,
                productSize = 512L * 1024 * 1024,
                bootSize = 64L * 1024 * 1024,
                recoverySize = 64L * 1024 * 1024,
                userdataSize = null,
                cacheSize = null
            ),
            requiredModifications = emptyList(),
            optionalModifications = emptyList(),
            outputFormats = listOf("fastboot", "recovery_zip")
        )
    }
    
    /**
     * Create generic Xiaomi profile
     */
    private fun createGenericXiaomiProfile(deviceInfo: DeviceInfo): DeviceProfile {
        return DeviceProfile(
            deviceName = "${deviceInfo.manufacturer} ${deviceInfo.model}",
            codename = deviceInfo.codename,
            manufacturer = deviceInfo.manufacturer,
            architecture = deviceInfo.architecture,
            minAndroidVersion = deviceInfo.androidVersion,
            maxAndroidVersion = null,
            supportedPartitionLayouts = listOf(deviceInfo.partitionLayout),
            vendorOverlays = emptyList(),
            kernelConfig = null,
            bootloaderConfig = BootloaderConfig(
                type = "fastboot",
                isUnlocked = false,
                verificationDisabled = false,
                customRecoverySupported = true,
                flashingMethod = "fastboot"
            ),
            partitionSizes = PartitionSizes(
                systemSize = 3L * 1024 * 1024 * 1024,
                vendorSize = 1L * 1024 * 1024 * 1024,
                productSize = 512L * 1024 * 1024,
                bootSize = 64L * 1024 * 1024,
                recoverySize = 64L * 1024 * 1024,
                userdataSize = null,
                cacheSize = null
            ),
            requiredModifications = listOf(
                DeviceModification(
                    name = "MIUI compatibility",
                    description = "Apply MIUI-specific modifications",
                    type = ModificationType.PROPERTY_MODIFICATION,
                    targetPath = "/system/build.prop",
                    action = ModificationAction.APPEND,
                    parameters = mapOf(
                        "ro.miui.ui.version.name" to "V12",
                        "ro.product.mod_device" to deviceInfo.codename
                    )
                )
            ),
            optionalModifications = emptyList(),
            outputFormats = listOf("fastboot", "recovery_zip")
        )
    }
    
    /**
     * Estimate partition sizes for Samsung devices
     */
    private fun estimateSamsungPartitionSizes(model: String): Pair<Long, Long> {
        return when {
            model.contains("S24", ignoreCase = true) || 
            model.contains("S23", ignoreCase = true) -> {
                Pair(6L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024) // 6GB system, 2GB vendor
            }
            model.contains("S22", ignoreCase = true) ||
            model.contains("S21", ignoreCase = true) -> {
                Pair(5L * 1024 * 1024 * 1024, 1536L * 1024 * 1024) // 5GB system, 1.5GB vendor
            }
            model.contains("Note", ignoreCase = true) -> {
                Pair(6L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024) // 6GB system, 2GB vendor
            }
            model.contains("A", ignoreCase = true) -> {
                Pair(4L * 1024 * 1024 * 1024, 1L * 1024 * 1024 * 1024) // 4GB system, 1GB vendor
            }
            else -> {
                Pair(4L * 1024 * 1024 * 1024, 1L * 1024 * 1024 * 1024) // Default 4GB system, 1GB vendor
            }
        }
    }
}
