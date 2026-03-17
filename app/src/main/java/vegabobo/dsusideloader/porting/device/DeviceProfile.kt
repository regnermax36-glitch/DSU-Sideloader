package vegabobo.dsusideloader.porting.device

/**
 * Device profile containing device-specific configuration for ROM porting
 * Defines compatibility requirements and modification parameters
 */
data class DeviceProfile(
    val deviceName: String,
    val codename: String,
    val manufacturer: String,
    val architecture: String, // arm64-v8a, armeabi-v7a, x86_64, x86
    val minAndroidVersion: Int,
    val maxAndroidVersion: Int?,
    val supportedPartitionLayouts: List<String>, // A-only, A/B, etc.
    val vendorOverlays: List<VendorOverlay>,
    val kernelConfig: KernelConfig?,
    val bootloaderConfig: BootloaderConfig,
    val partitionSizes: PartitionSizes,
    val requiredModifications: List<DeviceModification>,
    val optionalModifications: List<DeviceModification>,
    val outputFormats: List<String>, // recovery_zip, fastboot, odin, etc.
    val verificationKeys: List<String>? = null,
) {
    /**
     * Check if this profile is compatible with the given GSI
     */
    fun isCompatibleWith(
        gsiArchitecture: String,
        gsiAndroidVersion: Int,
        gsiPartitionLayout: String,
    ): Boolean {
        return architecture == gsiArchitecture &&
            gsiAndroidVersion >= minAndroidVersion &&
            (maxAndroidVersion == null || gsiAndroidVersion <= maxAndroidVersion!!) &&
            supportedPartitionLayouts.contains(gsiPartitionLayout)
    }

    /**
     * Get all modifications that should be applied
     */
    fun getAllModifications(): List<DeviceModification> {
        return requiredModifications + optionalModifications
    }
}

/**
 * Vendor overlay configuration
 */
data class VendorOverlay(
    val name: String,
    val sourcePath: String, // Path in vendor partition or overlay file
    val targetPath: String, // Target path in system
    val isRequired: Boolean = true,
    val conditions: List<String> = emptyList(), // Conditions for applying this overlay
)

/**
 * Kernel configuration for the device
 */
data class KernelConfig(
    val kernelVersion: String,
    val configPath: String?, // Path to kernel config file
    val requiredModules: List<String>,
    val bootImageFormat: String, // boot_v1, boot_v2, boot_v3, etc.
    val cmdlineParams: List<String>,
    val dtbPath: String? = null,
    val dtboPath: String? = null,
)

/**
 * Bootloader configuration
 */
data class BootloaderConfig(
    val type: String, // fastboot, odin, mtk, etc.
    val isUnlocked: Boolean,
    val verificationDisabled: Boolean,
    val customRecoverySupported: Boolean,
    val flashingMethod: String, // fastboot, odin, sp_flash_tool, etc.
    val partitionTable: String? = null,
)

/**
 * Partition size configuration
 */
data class PartitionSizes(
    val systemSize: Long, // in bytes
    val vendorSize: Long?,
    val productSize: Long?,
    val bootSize: Long,
    val recoverySize: Long?,
    val userdataSize: Long?,
    val cacheSize: Long?,
) {

    /**
     * Check if the given image sizes fit within partition limits
     */
    fun canFitImages(
        systemImageSize: Long,
        vendorImageSize: Long? = null,
        productImageSize: Long? = null,
    ): Boolean {
        if (systemImageSize > systemSize) return false
        if (vendorImageSize != null && vendorSize != null && vendorImageSize > vendorSize!!) return false
        if (productImageSize != null && productSize != null && productImageSize > productSize!!) return false
        return true
    }
}

/**
 * Device-specific modification
 */
data class DeviceModification(
    val name: String,
    val description: String,
    val type: ModificationType,
    val targetPath: String,
    val action: ModificationAction,
    val parameters: Map<String, String> = emptyMap(),
    val conditions: List<String> = emptyList(),
    val isRequired: Boolean = true,
)

/**
 * Types of modifications that can be applied
 */
enum class ModificationType {
    FILE_REPLACEMENT, // Replace a file with device-specific version
    FILE_PATCH, // Apply patches to existing files
    PROPERTY_MODIFICATION, // Modify build.prop or other property files
    PERMISSION_MODIFICATION, // Modify file permissions
    SYMLINK_CREATION, // Create symbolic links
    DIRECTORY_CREATION, // Create directories
    SELINUX_POLICY, // SELinux policy modifications
    INIT_SCRIPT, // Init script modifications
    VENDOR_OVERLAY, // Apply vendor-specific overlays
    KERNEL_MODULE, // Install kernel modules
}

/**
 * Actions that can be performed during modification
 */
enum class ModificationAction {
    COPY, // Copy file from source to target
    MOVE, // Move file from source to target
    DELETE, // Delete target file/directory
    PATCH, // Apply patch to target file
    APPEND, // Append content to target file
    PREPEND, // Prepend content to target file
    REPLACE_LINE, // Replace specific lines in target file
    SET_PERMISSION, // Set file/directory permissions
    CREATE_SYMLINK, // Create symbolic link
    EXTRACT_ARCHIVE, // Extract archive to target location
    COMPRESS, // Compress target files
    DECOMPRESS, // Decompress target files
}

/**
 * Predefined device profiles for common devices
 */
object DeviceProfiles {

    /**
     * Generic ARM64 device profile
     */
    val GENERIC_ARM64 = DeviceProfile(
        deviceName = "Generic ARM64 Device",
        codename = "generic_arm64",
        manufacturer = "Generic",
        architecture = "arm64-v8a",
        minAndroidVersion = 10,
        maxAndroidVersion = null,
        supportedPartitionLayouts = listOf("A-only", "A/B"),
        vendorOverlays = emptyList(),
        kernelConfig = null,
        bootloaderConfig = BootloaderConfig(
            type = "fastboot",
            isUnlocked = true,
            verificationDisabled = true,
            customRecoverySupported = true,
            flashingMethod = "fastboot",
        ),
        partitionSizes = PartitionSizes(
            systemSize = 3L * 1024 * 1024 * 1024, // 3GB
            vendorSize = 1L * 1024 * 1024 * 1024, // 1GB
            productSize = 512L * 1024 * 1024, // 512MB
            bootSize = 64L * 1024 * 1024, // 64MB
            recoverySize = 64L * 1024 * 1024, // 64MB
            userdataSize = null,
            cacheSize = 256L * 1024 * 1024, // 256MB
        ),
        requiredModifications = listOf(
            DeviceModification(
                name = "Update build properties",
                description = "Update build.prop with device-specific properties",
                type = ModificationType.PROPERTY_MODIFICATION,
                targetPath = "/system/build.prop",
                action = ModificationAction.APPEND,
                parameters = mapOf(
                    "ro.product.device" to "generic_arm64",
                    "ro.product.model" to "Generic ARM64 Device",
                ),
            ),
        ),
        optionalModifications = emptyList(),
        outputFormats = listOf("recovery_zip", "fastboot", "system_image"),
    )

    /**
     * Samsung Galaxy device profile template
     */
    fun createSamsungProfile(
        deviceName: String,
        codename: String,
        androidVersion: Int,
        systemSize: Long,
        vendorSize: Long,
    ) = DeviceProfile(
        deviceName = deviceName,
        codename = codename,
        manufacturer = "Samsung",
        architecture = "arm64-v8a",
        minAndroidVersion = androidVersion,
        maxAndroidVersion = null,
        supportedPartitionLayouts = listOf("A/B"),
        vendorOverlays = listOf(
            VendorOverlay(
                name = "Samsung UI Framework",
                sourcePath = "/vendor/overlay/SamsungFramework.apk",
                targetPath = "/system/product/overlay/SamsungFramework.apk",
            ),
        ),
        kernelConfig = KernelConfig(
            kernelVersion = "4.19",
            configPath = null,
            requiredModules = listOf("samsung_battery", "samsung_display"),
            bootImageFormat = "boot_v2",
            cmdlineParams = listOf("androidboot.selinux=permissive"),
        ),
        bootloaderConfig = BootloaderConfig(
            type = "odin",
            isUnlocked = false,
            verificationDisabled = false,
            customRecoverySupported = true,
            flashingMethod = "odin",
            partitionTable = "gpt",
        ),
        partitionSizes = PartitionSizes(
            systemSize = systemSize,
            vendorSize = vendorSize,
            productSize = 512L * 1024 * 1024,
            bootSize = 96L * 1024 * 1024,
            recoverySize = 96L * 1024 * 1024,
            userdataSize = null,
            cacheSize = null,
        ),
        requiredModifications = listOf(
            DeviceModification(
                name = "Samsung vendor overlay",
                description = "Apply Samsung-specific vendor overlays",
                type = ModificationType.VENDOR_OVERLAY,
                targetPath = "/system/product/overlay/",
                action = ModificationAction.COPY,
            ),
            DeviceModification(
                name = "Samsung build properties",
                description = "Set Samsung-specific build properties",
                type = ModificationType.PROPERTY_MODIFICATION,
                targetPath = "/system/build.prop",
                action = ModificationAction.APPEND,
                parameters = mapOf(
                    "ro.product.manufacturer" to "samsung",
                    "ro.product.brand" to "samsung",
                ),
            ),
        ),
        optionalModifications = emptyList(),
        outputFormats = listOf("odin", "recovery_zip"),
    )
}
