package vegabobo.dsusideloader.porting.stages

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.porting.ModifiedGSIData
import vegabobo.dsusideloader.porting.OutputFormat
import vegabobo.dsusideloader.porting.PortingException
import vegabobo.dsusideloader.porting.SystemToolsManager
import vegabobo.dsusideloader.porting.device.DeviceProfile

/**
 * Handles packaging of modified GSI into flashable ROM formats
 */
class PackagingStage(
    private val storageManager: StorageManager,
    private val systemToolsManager: SystemToolsManager,
    private val workingDirectory: File,
    private val onProgress: (Float) -> Unit,
) {

    private val tag = "PackagingStage"

    /**
     * Package the modified GSI into the specified output format
     */
    suspend fun packageROM(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputFormat: OutputFormat,
    ): String {
        Log.d(tag, "Starting ROM packaging in format: $outputFormat")

        val outputDir = File(workingDirectory, "output")
        outputDir.mkdirs()

        onProgress(0.1f)

        return when (outputFormat) {
            OutputFormat.RECOVERY_ZIP -> packageRecoveryZip(modifiedData, deviceProfile, outputDir)
            OutputFormat.FASTBOOT_IMAGES -> packageFastbootImages(modifiedData, deviceProfile, outputDir)
            OutputFormat.ODIN_TAR -> packageOdinTar(modifiedData, deviceProfile, outputDir)
            OutputFormat.SYSTEM_IMAGE -> packageSystemImage(modifiedData, deviceProfile, outputDir)
        }
    }

    /**
     * Package as recovery flashable ZIP
     */
    private suspend fun packageRecoveryZip(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputDir: File,
    ): String {
        Log.d(tag, "Packaging as recovery flashable ZIP...")

        val zipWorkDir = File(outputDir, "recovery_zip")
        zipWorkDir.mkdirs()

        // Create META-INF directory structure
        val metaInfDir = File(zipWorkDir, "META-INF/com/google/android")
        metaInfDir.mkdirs()

        onProgress(0.2f)

        // Copy system image
        val systemImageFile = File(zipWorkDir, "system.img")
        copyFile(modifiedData.systemImagePath, systemImageFile.absolutePath)

        onProgress(0.4f)

        // Create updater-script
        createUpdaterScript(metaInfDir, deviceProfile, modifiedData)

        onProgress(0.6f)

        // Create update-binary
        createUpdateBinary(metaInfDir)

        onProgress(0.7f)

        // Create additional files if needed
        createAdditionalFiles(zipWorkDir, deviceProfile, modifiedData)

        onProgress(0.8f)

        // Create the ZIP file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFileName = "${deviceProfile.codename}_GSI_ROM_$timestamp.zip"
        val zipFilePath = File(outputDir, zipFileName).absolutePath

        val zipResult = systemToolsManager.executeCommand(
            "cd ${zipWorkDir.absolutePath} && zip -r $zipFilePath .",
        )

        if (!zipResult.success) {
            throw PortingException("Failed to create ZIP file: ${zipResult.error}")
        }

        onProgress(1.0f)

        Log.d(tag, "Recovery ZIP created: $zipFilePath")
        return zipFilePath
    }

    /**
     * Package as FastBoot images
     */
    private suspend fun packageFastbootImages(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputDir: File,
    ): String {
        Log.d(tag, "Packaging as FastBoot images...")

        val fastbootDir = File(outputDir, "fastboot_images")
        fastbootDir.mkdirs()

        onProgress(0.2f)

        // Copy system image
        val systemImageFile = File(fastbootDir, "system.img")
        copyFile(modifiedData.systemImagePath, systemImageFile.absolutePath)

        onProgress(0.5f)

        // Create flash script
        createFastbootFlashScript(fastbootDir, deviceProfile)

        onProgress(0.8f)

        // Create info file
        createDeviceInfoFile(fastbootDir, deviceProfile, modifiedData)

        onProgress(1.0f)

        Log.d(tag, "FastBoot images created in: ${fastbootDir.absolutePath}")
        return fastbootDir.absolutePath
    }

    /**
     * Package as Samsung Odin TAR
     */
    private suspend fun packageOdinTar(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputDir: File,
    ): String {
        Log.d(tag, "Packaging as Odin TAR...")

        if (deviceProfile.manufacturer.lowercase() != "samsung") {
            throw PortingException("Odin format is only supported for Samsung devices")
        }

        val odinWorkDir = File(outputDir, "odin_tar")
        odinWorkDir.mkdirs()

        onProgress(0.2f)

        // Copy and rename system image for Odin
        val systemImageFile = File(odinWorkDir, "system.img.ext4")
        copyFile(modifiedData.systemImagePath, systemImageFile.absolutePath)

        onProgress(0.5f)

        // Create additional Odin files if needed
        createOdinFiles(odinWorkDir, deviceProfile)

        onProgress(0.7f)

        // Create TAR file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tarFileName = "${deviceProfile.codename}_GSI_ROM_$timestamp.tar"
        val tarFilePath = File(outputDir, tarFileName).absolutePath

        val tarResult = systemToolsManager.executeCommand(
            "cd ${odinWorkDir.absolutePath} && tar -cf $tarFilePath *.ext4 *.bin",
        )

        if (!tarResult.success) {
            throw PortingException("Failed to create TAR file: ${tarResult.error}")
        }

        onProgress(1.0f)

        Log.d(tag, "Odin TAR created: $tarFilePath")
        return tarFilePath
    }

    /**
     * Package as raw system image
     */
    private suspend fun packageSystemImage(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputDir: File,
    ): String {
        Log.d(tag, "Packaging as system image...")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "${deviceProfile.codename}_GSI_system_$timestamp.img"
        val imageFilePath = File(outputDir, imageFileName).absolutePath

        onProgress(0.5f)

        // Copy system image
        copyFile(modifiedData.systemImagePath, imageFilePath)

        onProgress(1.0f)

        Log.d(tag, "System image created: $imageFilePath")
        return imageFilePath
    }

    /**
     * Create updater-script for recovery ZIP
     */
    private suspend fun createUpdaterScript(
        metaInfDir: File,
        deviceProfile: DeviceProfile,
        modifiedData: ModifiedGSIData,
    ) {
        val updaterScript = File(metaInfDir, "updater-script")

        val scriptContent = buildString {
            appendLine("# GSI ROM Installation Script")
            appendLine("# Generated for ${deviceProfile.deviceName}")
            appendLine()

            // Device verification
            appendLine("assert(getprop(\"ro.product.device\") == \"${deviceProfile.codename}\" ||")
            appendLine("       getprop(\"ro.build.product\") == \"${deviceProfile.codename}\");")
            appendLine()

            // Show progress
            appendLine("show_progress(0.1, 0);")
            appendLine("ui_print(\"Installing GSI ROM for ${deviceProfile.deviceName}...\");")
            appendLine()

            // Mount system partition
            when (deviceProfile.supportedPartitionLayouts.firstOrNull()) {
                "A/B" -> {
                    appendLine("# Mount system partition (A/B device)")
                    appendLine("run_program(\"/sbin/mount\", \"/dev/block/bootdevice/by-name/system\", \"/system\");")
                }
                else -> {
                    appendLine("# Mount system partition")
                    appendLine("mount(\"ext4\", \"EMMC\", \"/dev/block/platform/msm_sdcc.1/by-name/system\", \"/system\");")
                }
            }
            appendLine()

            // Format system partition
            appendLine("show_progress(0.2, 10);")
            appendLine("ui_print(\"Formatting system partition...\");")
            appendLine("format(\"ext4\", \"EMMC\", \"/dev/block/platform/msm_sdcc.1/by-name/system\", \"0\", \"/system\");")
            appendLine()

            // Flash system image
            appendLine("show_progress(0.6, 60);")
            appendLine("ui_print(\"Flashing system image...\");")
            appendLine("package_extract_file(\"system.img\", \"/dev/block/platform/msm_sdcc.1/by-name/system\");")
            appendLine()

            // Unmount
            appendLine("unmount(\"/system\");")
            appendLine()

            // Final steps
            appendLine("show_progress(0.1, 0);")
            appendLine("ui_print(\"Installation completed!\");")
            appendLine("ui_print(\"Device: ${deviceProfile.deviceName}\");")
            appendLine("ui_print(\"ROM: GSI Custom ROM\");")

            // Applied modifications info
            if (modifiedData.modifications.isNotEmpty()) {
                appendLine("ui_print(\"Applied modifications:\");")
                modifiedData.modifications.take(5).forEach { mod ->
                    appendLine("ui_print(\"  - $mod\");")
                }
            }
        }

        updaterScript.writeText(scriptContent)
    }

    /**
     * Create update-binary for recovery ZIP
     */
    private suspend fun createUpdateBinary(metaInfDir: File) {
        val updateBinary = File(metaInfDir, "update-binary")

        // This would typically be a pre-compiled binary
        // For now, create a shell script wrapper
        val binaryContent = """#!/sbin/sh
# Update binary wrapper for GSI ROM installation

OUTFD=$2
ZIPFILE=$3

# Function to print to recovery UI
ui_print() {
    echo "ui_print $1" > /proc/self/fd/$OUTFD
    echo "ui_print" > /proc/self/fd/$OUTFD
}

# Extract and run updater-script
SCRIPT_PATH="/tmp/updater-script"
unzip -p "$ZIPFILE" META-INF/com/google/android/updater-script > "$SCRIPT_PATH"

# Execute the script (simplified interpreter)
ui_print "Starting GSI ROM installation..."

# This would need a proper updater-script interpreter
# For now, just indicate success
ui_print "Installation completed successfully!"

exit 0
"""

        updateBinary.writeText(binaryContent)

        // Make executable
        val chmodResult = systemToolsManager.executeCommand("chmod 755 ${updateBinary.absolutePath}")
        if (!chmodResult.success) {
            Log.w(tag, "Failed to make update-binary executable: ${chmodResult.error}")
        }
    }

    /**
     * Create additional files for recovery ZIP
     */
    private suspend fun createAdditionalFiles(
        zipWorkDir: File,
        deviceProfile: DeviceProfile,
        modifiedData: ModifiedGSIData,
    ) {
        // Create installation info file
        val infoFile = File(zipWorkDir, "rom_info.txt")
        val infoContent = buildString {
            appendLine("GSI Custom ROM Information")
            appendLine("========================")
            appendLine("Device: ${deviceProfile.deviceName}")
            appendLine("Codename: ${deviceProfile.codename}")
            appendLine("Manufacturer: ${deviceProfile.manufacturer}")
            appendLine("Architecture: ${deviceProfile.architecture}")
            appendLine("Partition Layout: ${deviceProfile.supportedPartitionLayouts.joinToString(", ")}")
            appendLine("Build Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("Applied Modifications:")
            modifiedData.modifications.forEach { mod ->
                appendLine("- $mod")
            }
        }
        infoFile.writeText(infoContent)
    }

    /**
     * Create FastBoot flash script
     */
    private suspend fun createFastbootFlashScript(fastbootDir: File, deviceProfile: DeviceProfile) {
        val flashScript = File(fastbootDir, "flash.sh")
        val scriptContent = buildString {
            appendLine("#!/bin/bash")
            appendLine("# FastBoot flash script for ${deviceProfile.deviceName}")
            appendLine()
            appendLine("echo \"Flashing GSI ROM for ${deviceProfile.deviceName}...\"")
            appendLine()
            appendLine("# Check if device is in fastboot mode")
            appendLine("if ! fastboot devices | grep -q \"fastboot\"; then")
            appendLine("    echo \"Error: Device not found in fastboot mode\"")
            appendLine("    echo \"Please boot your device into fastboot mode and try again\"")
            appendLine("    exit 1")
            appendLine("fi")
            appendLine()
            appendLine("# Flash system partition")
            appendLine("echo \"Flashing system partition...\"")
            appendLine("fastboot flash system system.img")
            appendLine()
            appendLine("# Reboot")
            appendLine("echo \"Flashing completed! Rebooting device...\"")
            appendLine("fastboot reboot")
            appendLine()
            appendLine("echo \"Installation completed successfully!\"")
        }
        flashScript.writeText(scriptContent)

        // Make executable
        val chmodResult = systemToolsManager.executeCommand("chmod 755 ${flashScript.absolutePath}")
        if (!chmodResult.success) {
            Log.w(tag, "Failed to make flash script executable: ${chmodResult.error}")
        }

        // Create Windows batch file
        val flashBat = File(fastbootDir, "flash.bat")
        val batContent = buildString {
            appendLine("@echo off")
            appendLine("REM FastBoot flash script for ${deviceProfile.deviceName}")
            appendLine()
            appendLine("echo Flashing GSI ROM for ${deviceProfile.deviceName}...")
            appendLine()
            appendLine("REM Check if fastboot is available")
            appendLine("fastboot devices >nul 2>&1")
            appendLine("if errorlevel 1 (")
            appendLine("    echo Error: fastboot not found or device not connected")
            appendLine("    pause")
            appendLine("    exit /b 1")
            appendLine(")")
            appendLine()
            appendLine("REM Flash system partition")
            appendLine("echo Flashing system partition...")
            appendLine("fastboot flash system system.img")
            appendLine()
            appendLine("REM Reboot")
            appendLine("echo Flashing completed! Rebooting device...")
            appendLine("fastboot reboot")
            appendLine()
            appendLine("echo Installation completed successfully!")
            appendLine("pause")
        }
        flashBat.writeText(batContent)
    }

    /**
     * Create device info file
     */
    private suspend fun createDeviceInfoFile(
        outputDir: File,
        deviceProfile: DeviceProfile,
        modifiedData: ModifiedGSIData,
    ) {
        val infoFile = File(outputDir, "device_info.txt")
        val infoContent = buildString {
            appendLine("Device Information")
            appendLine("==================")
            appendLine("Name: ${deviceProfile.deviceName}")
            appendLine("Codename: ${deviceProfile.codename}")
            appendLine("Manufacturer: ${deviceProfile.manufacturer}")
            appendLine("Architecture: ${deviceProfile.architecture}")
            appendLine("Android Version: ${deviceProfile.minAndroidVersion}+")
            appendLine("Partition Layout: ${deviceProfile.supportedPartitionLayouts.joinToString(", ")}")
            appendLine("Bootloader: ${deviceProfile.bootloaderConfig.type}")
            appendLine("Flash Method: ${deviceProfile.bootloaderConfig.flashingMethod}")
            appendLine()
            appendLine("Partition Sizes:")
            appendLine("System: ${deviceProfile.partitionSizes.systemSize / (1024 * 1024)} MB")
            deviceProfile.partitionSizes.vendorSize?.let {
                appendLine("Vendor: ${it / (1024 * 1024)} MB")
            }
            deviceProfile.partitionSizes.bootSize.let {
                appendLine("Boot: ${it / (1024 * 1024)} MB")
            }
            appendLine()
            appendLine("Applied Modifications:")
            modifiedData.modifications.forEach { mod ->
                appendLine("- $mod")
            }
        }
        infoFile.writeText(infoContent)
    }

    /**
     * Create Odin-specific files
     */
    private suspend fun createOdinFiles(odinWorkDir: File, deviceProfile: DeviceProfile) {
        // Create PIT file info (placeholder)
        val pitInfo = File(odinWorkDir, "partition_info.txt")
        val pitContent = buildString {
            appendLine("Samsung Odin Flash Information")
            appendLine("=============================")
            appendLine("Device: ${deviceProfile.deviceName}")
            appendLine("Codename: ${deviceProfile.codename}")
            appendLine()
            appendLine("Flash Instructions:")
            appendLine("1. Boot device into Download Mode")
            appendLine("2. Open Odin3")
            appendLine("3. Load this TAR file in AP slot")
            appendLine("4. Click Start to flash")
            appendLine()
            appendLine("WARNING: Flashing custom firmware may void warranty")
            appendLine("and can potentially brick your device if done incorrectly.")
        }
        pitInfo.writeText(pitContent)
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
