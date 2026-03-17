package vegabobo.dsusideloader.porting.stages

import android.net.Uri
import android.util.Log
import java.io.File
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.porting.ExtractedGSIData
import vegabobo.dsusideloader.porting.PortingException
import vegabobo.dsusideloader.porting.SystemToolsManager

/**
 * Handles GSI image extraction and analysis
 */
class ExtractionStage(
    private val storageManager: StorageManager,
    private val systemToolsManager: SystemToolsManager,
    private val workingDirectory: File,
    private val onProgress: (Float) -> Unit,
) {

    private val tag = "ExtractionStage"

    /**
     * Extract GSI image and analyze its contents
     */
    suspend fun extractGSI(gsiImageUri: Uri): ExtractedGSIData {
        Log.d(tag, "Starting GSI extraction...")

        val extractedDir = File(workingDirectory, "extracted")
        val filename = storageManager.getFilenameFromUri(gsiImageUri)

        onProgress(0.1f)

        return when {
            filename.endsWith(".img") -> extractRawImage(gsiImageUri, extractedDir)
            filename.endsWith(".zip") -> extractZipPackage(gsiImageUri, extractedDir)
            filename.endsWith(".gz") || filename.endsWith(".gzip") -> extractCompressedImage(gsiImageUri, extractedDir)
            filename.endsWith(".xz") -> extractXzImage(gsiImageUri, extractedDir)
            else -> throw PortingException("Unsupported GSI format: $filename")
        }
    }

    /**
     * Extract raw IMG file
     */
    private suspend fun extractRawImage(imageUri: Uri, extractedDir: File): ExtractedGSIData {
        Log.d(tag, "Extracting raw image...")

        // Copy image to working directory
        val imagePath = File(extractedDir, "system.img").absolutePath
        copyFileFromUri(imageUri, imagePath)

        onProgress(0.5f)

        // Mount and analyze the image
        return analyzeSystemImage(imagePath)
    }

    /**
     * Extract ZIP package (DSU format)
     */
    private suspend fun extractZipPackage(zipUri: Uri, extractedDir: File): ExtractedGSIData {
        Log.d(tag, "Extracting ZIP package...")

        val zipPath = File(extractedDir, "gsi_package.zip").absolutePath
        copyFileFromUri(zipUri, zipPath)

        onProgress(0.3f)

        // Extract ZIP contents
        val result = systemToolsManager.executeCommand("unzip -o $zipPath -d ${extractedDir.absolutePath}")
        if (!result.success) {
            throw PortingException("Failed to extract ZIP: ${result.error}")
        }

        onProgress(0.6f)

        // Find system image in extracted contents
        val systemImageFile = findSystemImageInDirectory(extractedDir)
            ?: throw PortingException("No system image found in ZIP package")

        return analyzeSystemImage(systemImageFile.absolutePath)
    }

    /**
     * Extract compressed image (GZ/GZIP)
     */
    private suspend fun extractCompressedImage(compressedUri: Uri, extractedDir: File): ExtractedGSIData {
        Log.d(tag, "Extracting compressed image...")

        val compressedPath = File(extractedDir, "system.img.gz").absolutePath
        copyFileFromUri(compressedUri, compressedPath)

        onProgress(0.3f)

        // Decompress the image
        val imagePath = File(extractedDir, "system.img").absolutePath
        val result = systemToolsManager.executeCommand("gunzip -c $compressedPath > $imagePath")
        if (!result.success) {
            throw PortingException("Failed to decompress image: ${result.error}")
        }

        onProgress(0.6f)

        return analyzeSystemImage(imagePath)
    }

    /**
     * Extract XZ compressed image
     */
    private suspend fun extractXzImage(xzUri: Uri, extractedDir: File): ExtractedGSIData {
        Log.d(tag, "Extracting XZ compressed image...")

        val xzPath = File(extractedDir, "system.img.xz").absolutePath
        copyFileFromUri(xzUri, xzPath)

        onProgress(0.3f)

        // Decompress the image using xz (if available) or fallback to manual extraction
        val imagePath = File(extractedDir, "system.img").absolutePath

        val result = if (systemToolsManager.isToolAvailable("xz")) {
            systemToolsManager.executeCommand("xz -d -c $xzPath > $imagePath")
        } else {
            // Fallback to manual XZ extraction using Java
            extractXzManually(xzPath, imagePath)
        }

        if (!result.success) {
            throw PortingException("Failed to decompress XZ image: ${result.error}")
        }

        onProgress(0.6f)

        return analyzeSystemImage(imagePath)
    }

    /**
     * Manual XZ extraction fallback
     */
    private suspend fun extractXzManually(xzPath: String, outputPath: String): SystemToolsManager.CommandResult {
        return try {
            // This would require implementing XZ decompression in Java
            // For now, return an error to indicate XZ tool is required
            SystemToolsManager.CommandResult(
                success = false,
                output = "",
                error = "XZ tool not available and manual extraction not implemented",
                exitCode = 1,
            )
        } catch (e: Exception) {
            SystemToolsManager.CommandResult(
                success = false,
                output = "",
                error = e.message ?: "XZ extraction failed",
                exitCode = 1,
            )
        }
    }

    /**
     * Analyze system image to extract metadata
     */
    private suspend fun analyzeSystemImage(imagePath: String): ExtractedGSIData {
        Log.d(tag, "Analyzing system image...")

        val mountPoint = File(workingDirectory, "mount_system").absolutePath
        File(mountPoint).mkdirs()

        try {
            // Create loop device
            val loopResult = systemToolsManager.createLoopDevice(imagePath)
            if (!loopResult.success) {
                throw PortingException("Failed to create loop device: ${loopResult.error}")
            }

            val loopDevice = loopResult.output.trim()

            try {
                // Mount the image
                val mountResult = systemToolsManager.mountFilesystem(loopDevice, mountPoint, "ext4", "ro")
                if (!mountResult.success) {
                    throw PortingException("Failed to mount system image: ${mountResult.error}")
                }

                onProgress(0.8f)

                try {
                    // Extract build properties
                    val buildProperties = extractBuildProperties(mountPoint)

                    // Detect architecture
                    val architecture = detectArchitecture(mountPoint, buildProperties)

                    // Detect Android version
                    val androidVersion = detectAndroidVersion(buildProperties)

                    // Detect partition layout
                    val partitionLayout = detectPartitionLayout(mountPoint, buildProperties)

                    onProgress(1.0f)

                    return ExtractedGSIData(
                        systemImagePath = imagePath,
                        vendorImagePath = null, // GSIs typically don't include vendor
                        productImagePath = null,
                        architecture = architecture,
                        androidVersion = androidVersion,
                        partitionLayout = partitionLayout,
                        buildProperties = buildProperties,
                    )
                } finally {
                    // Unmount
                    systemToolsManager.unmountFilesystem(mountPoint)
                }
            } finally {
                // Remove loop device
                systemToolsManager.removeLoopDevice(loopDevice)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to analyze system image", e)
            throw PortingException("System image analysis failed: ${e.message}")
        }
    }

    /**
     * Extract build properties from mounted system
     */
    private suspend fun extractBuildProperties(mountPoint: String): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        val buildPropFiles = listOf(
            "$mountPoint/build.prop",
            "$mountPoint/system/build.prop",
            "$mountPoint/product/build.prop",
            "$mountPoint/vendor/build.prop",
        )

        for (propFile in buildPropFiles) {
            if (File(propFile).exists()) {
                val result = systemToolsManager.executeCommand("cat $propFile")
                if (result.success) {
                    parseBuildProperties(result.output, properties)
                }
            }
        }

        return properties
    }

    /**
     * Parse build properties from text content
     */
    private fun parseBuildProperties(content: String, properties: MutableMap<String, String>) {
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    properties[parts[0].trim()] = parts[1].trim()
                }
            }
        }
    }

    /**
     * Detect architecture from system image
     */
    private suspend fun detectArchitecture(mountPoint: String, buildProperties: Map<String, String>): String {
        // Check build properties first
        buildProperties["ro.product.cpu.abi"]?.let { abi ->
            return when (abi) {
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> abi
            }
        }

        // Check for architecture-specific files
        val archDirs = listOf(
            "$mountPoint/lib64" to "arm64-v8a",
            "$mountPoint/lib" to "armeabi-v7a",
        )

        for ((dir, arch) in archDirs) {
            if (File(dir).exists()) {
                return arch
            }
        }

        return "arm64-v8a" // Default fallback
    }

    /**
     * Detect Android version from build properties
     */
    private fun detectAndroidVersion(buildProperties: Map<String, String>): Int {
        buildProperties["ro.build.version.sdk"]?.toIntOrNull()?.let { return it }
        buildProperties["ro.build.version.release"]?.let { release ->
            return when (release) {
                "10" -> 29
                "11" -> 30
                "12" -> 31
                "13" -> 33
                "14" -> 34
                else -> 29 // Default to Android 10
            }
        }
        return 29 // Default fallback
    }

    /**
     * Detect partition layout from system image
     */
    private fun detectPartitionLayout(mountPoint: String, buildProperties: Map<String, String>): String {
        // Check build properties
        if (buildProperties["ro.build.ab_update"] == "true") {
            return "A/B"
        }

        // Check for A/B specific files
        if (File("$mountPoint/system/etc/update_engine").exists()) {
            return "A/B"
        }

        return "A-only"
    }

    /**
     * Find system image file in directory
     */
    private fun findSystemImageInDirectory(directory: File): File? {
        val imageExtensions = listOf(".img", ".raw")
        val systemNames = listOf("system", "super", "gsi")

        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                val name = file.name.lowercase()
                for (sysName in systemNames) {
                    for (ext in imageExtensions) {
                        if (name.contains(sysName) && name.endsWith(ext)) {
                            return file
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Copy file from URI to local path
     */
    private suspend fun copyFileFromUri(uri: Uri, targetPath: String) {
        try {
            val inputStream = storageManager.openInputStream(uri)
            val outputFile = File(targetPath)
            outputFile.parentFile?.mkdirs()

            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw PortingException("Failed to copy file: ${e.message}")
        }
    }
}
