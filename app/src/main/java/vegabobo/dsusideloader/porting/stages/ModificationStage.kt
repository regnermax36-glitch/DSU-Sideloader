package vegabobo.dsusideloader.porting.stages

import android.util.Log
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.porting.ExtractedGSIData
import vegabobo.dsusideloader.porting.ModifiedGSIData
import vegabobo.dsusideloader.porting.PortingException
import vegabobo.dsusideloader.porting.SystemToolsManager
import vegabobo.dsusideloader.porting.device.DeviceModification
import vegabobo.dsusideloader.porting.device.DeviceProfile
import vegabobo.dsusideloader.porting.device.ModificationAction
import vegabobo.dsusideloader.porting.device.ModificationType
import java.io.File

/**
 * Handles device-specific modifications to the extracted GSI
 */
class ModificationStage(
    private val storageManager: StorageManager,
    private val systemToolsManager: SystemToolsManager,
    private val workingDirectory: File,
    private val onProgress: (Float) -> Unit
) {
    
    private val tag = "ModificationStage"
    
    /**
     * Apply device-specific modifications to the GSI
     */
    suspend fun applyModifications(
        extractedData: ExtractedGSIData,
        deviceProfile: DeviceProfile
    ): ModifiedGSIData {
        Log.d(tag, "Starting device-specific modifications...")
        
        val modifiedDir = File(workingDirectory, "modified")
        modifiedDir.mkdirs()
        
        // Copy system image to modification directory
        val modifiedSystemPath = File(modifiedDir, "system.img").absolutePath
        copyFile(extractedData.systemImagePath, modifiedSystemPath)
        
        onProgress(0.1f)
        
        // Mount the system image for modification
        val mountPoint = File(workingDirectory, "mount_modified").absolutePath
        File(mountPoint).mkdirs()
        
        val appliedModifications = mutableListOf<String>()
        
        try {
            // Create loop device and mount
            val loopResult = systemToolsManager.createLoopDevice(modifiedSystemPath)
            if (!loopResult.success) {
                throw PortingException("Failed to create loop device: ${loopResult.error}")
            }
            
            val loopDevice = loopResult.output.trim()
            
            try {
                // Mount as read-write
                val mountResult = systemToolsManager.mountFilesystem(loopDevice, mountPoint, "ext4", "rw")
                if (!mountResult.success) {
                    throw PortingException("Failed to mount system image: ${mountResult.error}")
                }
                
                try {
                    // Apply all device modifications
                    val modifications = deviceProfile.getAllModifications()
                    val totalModifications = modifications.size
                    
                    modifications.forEachIndexed { index, modification ->
                        Log.d(tag, "Applying modification: ${modification.name}")
                        
                        try {
                            applyModification(modification, mountPoint, deviceProfile)
                            appliedModifications.add(modification.name)
                            Log.d(tag, "Successfully applied: ${modification.name}")
                        } catch (e: Exception) {
                            if (modification.isRequired) {
                                throw PortingException("Required modification failed: ${modification.name} - ${e.message}")
                            } else {
                                Log.w(tag, "Optional modification failed: ${modification.name} - ${e.message}")
                                appliedModifications.add("${modification.name} (FAILED)")
                            }
                        }
                        
                        onProgress(0.1f + (index + 1).toFloat() / totalModifications * 0.7f)
                    }
                    
                    // Apply device-specific build properties
                    applyDeviceBuildProperties(mountPoint, deviceProfile, extractedData)
                    appliedModifications.add("Device build properties")
                    
                    onProgress(0.9f)
                    
                    // Resize filesystem if needed
                    resizeFilesystemIfNeeded(loopDevice, deviceProfile)
                    
                    onProgress(1.0f)
                    
                } finally {
                    // Unmount
                    systemToolsManager.unmountFilesystem(mountPoint)
                }
                
            } finally {
                // Remove loop device
                systemToolsManager.removeLoopDevice(loopDevice)
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Modification stage failed", e)
            throw PortingException("Modification failed: ${e.message}")
        }
        
        return ModifiedGSIData(
            systemImagePath = modifiedSystemPath,
            vendorImagePath = null, // Will be created if needed
            productImagePath = null,
            bootImagePath = null, // Will be created if needed
            recoveryImagePath = null,
            modifications = appliedModifications
        )
    }
    
    /**
     * Apply a single device modification
     */
    private suspend fun applyModification(
        modification: DeviceModification,
        mountPoint: String,
        deviceProfile: DeviceProfile
    ) {
        Log.d(tag, "Applying ${modification.type} modification: ${modification.name}")
        
        when (modification.type) {
            ModificationType.PROPERTY_MODIFICATION -> applyPropertyModification(modification, mountPoint)
            ModificationType.FILE_REPLACEMENT -> applyFileReplacement(modification, mountPoint)
            ModificationType.FILE_PATCH -> applyFilePatch(modification, mountPoint)
            ModificationType.PERMISSION_MODIFICATION -> applyPermissionModification(modification, mountPoint)
            ModificationType.SYMLINK_CREATION -> applySymlinkCreation(modification, mountPoint)
            ModificationType.DIRECTORY_CREATION -> applyDirectoryCreation(modification, mountPoint)
            ModificationType.VENDOR_OVERLAY -> applyVendorOverlay(modification, mountPoint, deviceProfile)
            ModificationType.INIT_SCRIPT -> applyInitScript(modification, mountPoint)
            ModificationType.SELINUX_POLICY -> applySelinuxPolicy(modification, mountPoint)
            ModificationType.KERNEL_MODULE -> applyKernelModule(modification, mountPoint)
        }
    }
    
    /**
     * Apply property modifications to build.prop files
     */
    private suspend fun applyPropertyModification(modification: DeviceModification, mountPoint: String) {
        val targetFile = File(mountPoint, modification.targetPath.removePrefix("/"))
        
        when (modification.action) {
            ModificationAction.APPEND -> {
                val content = modification.parameters.map { (key, value) ->
                    "$key=$value"
                }.joinToString("\n", prefix = "\n# Device-specific properties\n", postfix = "\n")
                
                targetFile.appendText(content)
            }
            
            ModificationAction.REPLACE_LINE -> {
                val lines = targetFile.readLines().toMutableList()
                modification.parameters.forEach { (key, value) ->
                    val lineIndex = lines.indexOfFirst { it.startsWith("$key=") }
                    if (lineIndex >= 0) {
                        lines[lineIndex] = "$key=$value"
                    } else {
                        lines.add("$key=$value")
                    }
                }
                targetFile.writeText(lines.joinToString("\n"))
            }
            
            else -> throw PortingException("Unsupported action for property modification: ${modification.action}")
        }
    }
    
    /**
     * Apply file replacement
     */
    private suspend fun applyFileReplacement(modification: DeviceModification, mountPoint: String) {
        val sourcePath = modification.parameters["source_path"]
            ?: throw PortingException("Source path not specified for file replacement")
        
        val targetFile = File(mountPoint, modification.targetPath.removePrefix("/"))
        val sourceFile = File(sourcePath)
        
        if (!sourceFile.exists()) {
            throw PortingException("Source file not found: $sourcePath")
        }
        
        targetFile.parentFile?.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
    }
    
    /**
     * Apply file patches
     */
    private suspend fun applyFilePatch(modification: DeviceModification, mountPoint: String) {
        val patchPath = modification.parameters["patch_path"]
            ?: throw PortingException("Patch path not specified")
        
        val targetFile = File(mountPoint, modification.targetPath.removePrefix("/"))
        
        if (!targetFile.exists()) {
            throw PortingException("Target file not found: ${targetFile.absolutePath}")
        }
        
        // Apply patch using system patch command if available
        val result = systemToolsManager.executeCommand("patch ${targetFile.absolutePath} < $patchPath")
        if (!result.success) {
            throw PortingException("Patch application failed: ${result.error}")
        }
    }
    
    /**
     * Apply permission modifications
     */
    private suspend fun applyPermissionModification(modification: DeviceModification, mountPoint: String) {
        val permissions = modification.parameters["permissions"]
            ?: throw PortingException("Permissions not specified")
        
        val targetPath = File(mountPoint, modification.targetPath.removePrefix("/")).absolutePath
        
        val result = systemToolsManager.executeCommand("chmod $permissions $targetPath")
        if (!result.success) {
            throw PortingException("Permission modification failed: ${result.error}")
        }
    }
    
    /**
     * Apply symlink creation
     */
    private suspend fun applySymlinkCreation(modification: DeviceModification, mountPoint: String) {
        val linkTarget = modification.parameters["link_target"]
            ?: throw PortingException("Link target not specified")
        
        val linkPath = File(mountPoint, modification.targetPath.removePrefix("/")).absolutePath
        
        val result = systemToolsManager.executeCommand("ln -sf $linkTarget $linkPath")
        if (!result.success) {
            throw PortingException("Symlink creation failed: ${result.error}")
        }
    }
    
    /**
     * Apply directory creation
     */
    private suspend fun applyDirectoryCreation(modification: DeviceModification, mountPoint: String) {
        val targetDir = File(mountPoint, modification.targetPath.removePrefix("/"))
        
        if (!targetDir.mkdirs() && !targetDir.exists()) {
            throw PortingException("Failed to create directory: ${targetDir.absolutePath}")
        }
        
        // Apply permissions if specified
        modification.parameters["permissions"]?.let { permissions ->
            val result = systemToolsManager.executeCommand("chmod $permissions ${targetDir.absolutePath}")
            if (!result.success) {
                Log.w(tag, "Failed to set directory permissions: ${result.error}")
            }
        }
    }
    
    /**
     * Apply vendor overlay
     */
    private suspend fun applyVendorOverlay(
        modification: DeviceModification,
        mountPoint: String,
        deviceProfile: DeviceProfile
    ) {
        deviceProfile.vendorOverlays.forEach { overlay ->
            if (overlay.isRequired || modification.parameters["apply_optional"] == "true") {
                val sourcePath = overlay.sourcePath
                val targetPath = File(mountPoint, overlay.targetPath.removePrefix("/"))
                
                // This would copy vendor overlay files
                // Implementation depends on where vendor overlays are stored
                Log.d(tag, "Applying vendor overlay: ${overlay.name}")
                
                targetPath.parentFile?.mkdirs()
                // Copy overlay files here
            }
        }
    }
    
    /**
     * Apply init script modifications
     */
    private suspend fun applyInitScript(modification: DeviceModification, mountPoint: String) {
        val scriptContent = modification.parameters["script_content"]
            ?: throw PortingException("Script content not specified")
        
        val targetFile = File(mountPoint, modification.targetPath.removePrefix("/"))
        targetFile.parentFile?.mkdirs()
        
        when (modification.action) {
            ModificationAction.APPEND -> targetFile.appendText("\n$scriptContent\n")
            ModificationAction.PREPEND -> {
                val existingContent = if (targetFile.exists()) targetFile.readText() else ""
                targetFile.writeText("$scriptContent\n$existingContent")
            }
            ModificationAction.COPY -> targetFile.writeText(scriptContent)
            else -> throw PortingException("Unsupported action for init script: ${modification.action}")
        }
        
        // Make script executable
        val result = systemToolsManager.executeCommand("chmod 755 ${targetFile.absolutePath}")
        if (!result.success) {
            Log.w(tag, "Failed to make script executable: ${result.error}")
        }
    }
    
    /**
     * Apply SELinux policy modifications
     */
    private suspend fun applySelinuxPolicy(modification: DeviceModification, mountPoint: String) {
        val policyPath = modification.parameters["policy_path"]
            ?: throw PortingException("Policy path not specified")
        
        val targetDir = File(mountPoint, "system/etc/selinux")
        targetDir.mkdirs()
        
        // Copy policy files
        val sourceFile = File(policyPath)
        if (sourceFile.exists()) {
            sourceFile.copyTo(File(targetDir, sourceFile.name), overwrite = true)
        }
    }
    
    /**
     * Apply kernel module installation
     */
    private suspend fun applyKernelModule(modification: DeviceModification, mountPoint: String) {
        val modulePath = modification.parameters["module_path"]
            ?: throw PortingException("Module path not specified")
        
        val targetDir = File(mountPoint, "system/lib/modules")
        targetDir.mkdirs()
        
        val sourceFile = File(modulePath)
        if (sourceFile.exists()) {
            sourceFile.copyTo(File(targetDir, sourceFile.name), overwrite = true)
        }
    }
    
    /**
     * Apply device-specific build properties
     */
    private suspend fun applyDeviceBuildProperties(
        mountPoint: String,
        deviceProfile: DeviceProfile,
        extractedData: ExtractedGSIData
    ) {
        val buildPropFile = File(mountPoint, "build.prop")
        
        val deviceProperties = mapOf(
            "ro.product.manufacturer" to deviceProfile.manufacturer,
            "ro.product.brand" to deviceProfile.manufacturer,
            "ro.product.device" to deviceProfile.codename,
            "ro.product.model" to deviceProfile.deviceName,
            "ro.product.name" to deviceProfile.codename,
            "ro.build.product" to deviceProfile.codename,
            "ro.build.device" to deviceProfile.codename,
            "ro.build.fingerprint" to generateBuildFingerprint(deviceProfile, extractedData),
            "ro.system.build.fingerprint" to generateBuildFingerprint(deviceProfile, extractedData)
        )
        
        val content = deviceProperties.map { (key, value) ->
            "$key=$value"
        }.joinToString("\n", prefix = "\n# Device-specific build properties\n", postfix = "\n")
        
        buildPropFile.appendText(content)
    }
    
    /**
     * Generate build fingerprint for the device
     */
    private fun generateBuildFingerprint(deviceProfile: DeviceProfile, extractedData: ExtractedGSIData): String {
        val buildId = extractedData.buildProperties["ro.build.id"] ?: "UNKNOWN"
        val buildVersion = extractedData.buildProperties["ro.build.version.release"] ?: "12"
        val buildDate = System.currentTimeMillis() / 1000
        
        return "${deviceProfile.manufacturer}/${deviceProfile.codename}/${deviceProfile.codename}:$buildVersion/$buildId/$buildDate:user/release-keys"
    }
    
    /**
     * Resize filesystem if needed to fit device partition
     */
    private suspend fun resizeFilesystemIfNeeded(loopDevice: String, deviceProfile: DeviceProfile) {
        // Check current filesystem size
        val result = systemToolsManager.executeCommand("dumpe2fs -h $loopDevice")
        if (!result.success) {
            Log.w(tag, "Could not check filesystem size: ${result.error}")
            return
        }
        
        // Calculate target size based on device partition size
        val targetSize = deviceProfile.partitionSizes.systemSize
        val targetSizeBlocks = targetSize / 4096 // Assuming 4K blocks
        
        // Resize filesystem
        val resizeResult = systemToolsManager.resizeFilesystem(loopDevice, "${targetSizeBlocks}s")
        if (!resizeResult.success) {
            Log.w(tag, "Filesystem resize failed: ${resizeResult.error}")
        } else {
            Log.d(tag, "Filesystem resized to $targetSize bytes")
        }
    }
    
    /**
     * Copy file from source to destination
     */
    private suspend fun copyFile(sourcePath: String, targetPath: String) {
        val result = systemToolsManager.executeCommand("cp $sourcePath $targetPath")
        if (!result.success) {
            throw PortingException("Failed to copy file: ${result.error}")
        }
    }
}
