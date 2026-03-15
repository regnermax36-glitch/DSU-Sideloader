package vegabobo.dsusideloader.porting

import android.app.Application
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Job
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.Session
import vegabobo.dsusideloader.porting.device.DeviceDetector
import vegabobo.dsusideloader.porting.device.DeviceProfile
import vegabobo.dsusideloader.preparation.InstallationStep

/**
 * Main ROM processor that orchestrates the complete GSI to custom ROM porting workflow
 * Replaces the original DSUInstaller with comprehensive ROM porting capabilities
 */
class ROMProcessor(
    private val application: Application,
    private val storageManager: StorageManager,
    private val session: Session,
    private val gsiImageUri: Uri,
    private val outputFormat: OutputFormat = OutputFormat.RECOVERY_ZIP,
    private var processingJob: Job = Job(),
    private val onError: (error: PortingError) -> Unit,
    private val onProgressUpdate: (progress: Float, stage: String) -> Unit,
    private val onStepUpdate: (step: PortingStep) -> Unit,
    private val onSuccess: (outputPath: String, deviceProfile: DeviceProfile) -> Unit
) {
    
    private val tag = "ROMProcessor"
    
    private lateinit var systemToolsManager: SystemToolsManager
    private lateinit var deviceDetector: DeviceDetector
    private lateinit var portingPipeline: PortingPipeline
    
    /**
     * Initialize the ROM processor
     */
    suspend fun initialize(): Boolean {
        Log.d(tag, "Initializing ROM processor...")
        
        try {
            // Initialize system tools manager
            systemToolsManager = SystemToolsManager()
            val toolsAvailable = systemToolsManager.initialize()
            
            if (!toolsAvailable) {
                Log.e(tag, "Required system tools not available")
                onError(PortingError.PIPELINE_FAILED("Required system tools not available"))
                return false
            }
            
            // Initialize device detector
            deviceDetector = DeviceDetector(systemToolsManager)
            
            Log.d(tag, "ROM processor initialized successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize ROM processor", e)
            onError(PortingError.PIPELINE_FAILED("Initialization failed: ${e.message}"))
            return false
        }
    }
    
    /**
     * Start the complete ROM porting process
     */
    suspend fun startPorting() {
        Log.d(tag, "Starting ROM porting process...")
        
        try {
            onStepUpdate(PortingStep.INITIALIZING)
            onProgressUpdate(0.05f, "Initializing")
            
            // Detect current device
            val deviceInfo = deviceDetector.detectDevice()
            Log.d(tag, "Detected device: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            
            onProgressUpdate(0.1f, "Device detected")
            
            // Find matching device profile
            val deviceProfile = deviceDetector.findMatchingProfile(deviceInfo)
            if (deviceProfile == null) {
                throw PortingException("No compatible device profile found for ${deviceInfo.manufacturer} ${deviceInfo.model}")
            }
            
            Log.d(tag, "Using device profile: ${deviceProfile.deviceName}")
            onProgressUpdate(0.15f, "Device profile loaded")
            
            // Validate system tools for this device
            validateSystemToolsForDevice(deviceProfile)
            
            onProgressUpdate(0.2f, "System validation complete")
            
            // Initialize porting pipeline
            portingPipeline = PortingPipeline(
                storageManager = storageManager,
                systemToolsManager = systemToolsManager,
                session = session,
                job = processingJob,
                onStepUpdate = onStepUpdate,
                onProgressUpdate = { progress, stage ->
                    // Map pipeline progress to overall progress (20% to 95%)
                    val overallProgress = 0.2f + (progress * 0.75f)
                    onProgressUpdate(overallProgress, stage)
                },
                onError = onError,
                onSuccess = { outputPath ->
                    onProgressUpdate(1.0f, "Completed")
                    onSuccess(outputPath, deviceProfile)
                }
            )
            
            // Start the porting pipeline
            portingPipeline.startPorting(gsiImageUri, deviceProfile, outputFormat)
            
        } catch (e: Exception) {
            Log.e(tag, "ROM porting failed", e)
            onError(PortingError.PIPELINE_FAILED(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Validate that required system tools are available for the target device
     */
    private suspend fun validateSystemToolsForDevice(deviceProfile: DeviceProfile) {
        Log.d(tag, "Validating system tools for device...")
        
        val toolsInfo = systemToolsManager.getToolsInfo()
        val missingRequiredTools = toolsInfo.filter { it.isRequired && !it.isAvailable }
        
        if (missingRequiredTools.isNotEmpty()) {
            val missingToolNames = missingRequiredTools.map { it.name }
            throw PortingException("Missing required tools: ${missingToolNames.joinToString(", ")}")
        }
        
        // Check device-specific tool requirements
        when (deviceProfile.bootloaderConfig.flashingMethod) {
            "fastboot" -> {
                if (!systemToolsManager.isToolAvailable("fastboot")) {
                    Log.w(tag, "FastBoot tool not available - FastBoot output format may not work")
                }
            }
            "odin" -> {
                if (!systemToolsManager.isToolAvailable("tar")) {
                    throw PortingException("TAR tool required for Odin format but not available")
                }
            }
        }
        
        // Check for compression tools if needed
        if (outputFormat == OutputFormat.RECOVERY_ZIP && !systemToolsManager.isToolAvailable("zip")) {
            throw PortingException("ZIP tool required for recovery ZIP format but not available")
        }
        
        Log.d(tag, "System tools validation passed")
    }
    
    /**
     * Get detailed information about available system tools
     */
    fun getSystemToolsInfo(): List<SystemToolsManager.ToolInfo> {
        return if (::systemToolsManager.isInitialized) {
            systemToolsManager.getToolsInfo()
        } else {
            emptyList()
        }
    }
    
    /**
     * Check if the processor is ready for porting
     */
    fun isReady(): Boolean {
        return ::systemToolsManager.isInitialized && 
               systemToolsManager.areRequiredToolsAvailable()
    }
    
    /**
     * Cancel the current porting operation
     */
    fun cancel() {
        Log.d(tag, "Cancelling ROM porting operation...")
        
        processingJob.cancel()
        
        if (::portingPipeline.isInitialized) {
            portingPipeline.cancel()
        }
    }
    
    /**
     * Get current device information
     */
    suspend fun getCurrentDeviceInfo(): DeviceDetector.DeviceInfo? {
        return if (::deviceDetector.isInitialized) {
            try {
                deviceDetector.detectDevice()
            } catch (e: Exception) {
                Log.e(tag, "Failed to get device info", e)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Get available device profiles for manual selection
     */
    fun getAvailableDeviceProfiles(): List<DeviceProfile> {
        // This would return a list of all available device profiles
        // For now, return some common profiles
        return listOf(
            vegabobo.dsusideloader.porting.device.DeviceProfiles.GENERIC_ARM64,
            vegabobo.dsusideloader.porting.device.DeviceProfiles.createSamsungProfile(
                "Samsung Galaxy S23", "dm1q", 33, 
                6L * 1024 * 1024 * 1024, 2L * 1024 * 1024 * 1024
            )
        )
    }
    
    /**
     * Validate GSI compatibility with device
     */
    suspend fun validateGSICompatibility(
        gsiUri: Uri,
        deviceProfile: DeviceProfile
    ): ValidationResult {
        Log.d(tag, "Validating GSI compatibility...")
        
        return try {
            // Quick validation without full extraction
            val filename = storageManager.getFilenameFromUri(gsiUri)
            val fileSize = storageManager.getFilesizeFromUri(gsiUri)
            
            // Check file format
            val supportedFormats = listOf(".img", ".zip", ".gz", ".xz")
            val isFormatSupported = supportedFormats.any { filename.endsWith(it) }
            
            if (!isFormatSupported) {
                return ValidationResult(
                    isCompatible = false,
                    issues = listOf("Unsupported file format: $filename"),
                    warnings = emptyList()
                )
            }
            
            // Check file size against device partition size
            val issues = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            if (fileSize > deviceProfile.partitionSizes.systemSize) {
                issues.add("GSI file size (${fileSize / (1024 * 1024)} MB) exceeds device system partition size (${deviceProfile.partitionSizes.systemSize / (1024 * 1024)} MB)")
            }
            
            // Add architecture warning if we can't determine it from filename
            if (!filename.contains("arm64") && !filename.contains("arm32") && !filename.contains("x86")) {
                warnings.add("Cannot determine GSI architecture from filename - compatibility will be verified during extraction")
            }
            
            ValidationResult(
                isCompatible = issues.isEmpty(),
                issues = issues,
                warnings = warnings
            )
            
        } catch (e: Exception) {
            Log.e(tag, "GSI validation failed", e)
            ValidationResult(
                isCompatible = false,
                issues = listOf("Validation failed: ${e.message}"),
                warnings = emptyList()
            )
        }
    }
    
    /**
     * Get estimated processing time based on GSI size and device profile
     */
    suspend fun getEstimatedProcessingTime(gsiUri: Uri, deviceProfile: DeviceProfile): Long {
        val fileSize = storageManager.getFilesizeFromUri(gsiUri)
        val fileSizeMB = fileSize / (1024 * 1024)
        
        // Rough estimation based on file size and complexity
        val baseTimeMinutes = when {
            fileSizeMB < 1000 -> 5  // < 1GB
            fileSizeMB < 2000 -> 10 // 1-2GB
            fileSizeMB < 4000 -> 20 // 2-4GB
            else -> 30              // > 4GB
        }
        
        // Add time for device-specific modifications
        val modificationTime = deviceProfile.getAllModifications().size * 2
        
        return (baseTimeMinutes + modificationTime) * 60 * 1000L // Convert to milliseconds
    }
}

/**
 * GSI validation result
 */
data class ValidationResult(
    val isCompatible: Boolean,
    val issues: List<String>,
    val warnings: List<String>
)
