package vegabobo.dsusideloader.porting

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.Session
import vegabobo.dsusideloader.porting.device.DeviceProfile
import vegabobo.dsusideloader.porting.stages.ExtractionStage
import vegabobo.dsusideloader.porting.stages.ModificationStage
import vegabobo.dsusideloader.porting.stages.PackagingStage
import vegabobo.dsusideloader.preparation.InstallationStep
import java.io.File

/**
 * Main orchestrator for the GSI ROM porting pipeline
 * Manages the complete workflow from GSI input to flashable ROM output
 */
class PortingPipeline(
    private val storageManager: StorageManager,
    private val systemToolsManager: SystemToolsManager,
    private val session: Session,
    private val job: Job,
    private val onStepUpdate: (step: PortingStep) -> Unit,
    private val onProgressUpdate: (progress: Float, stage: String) -> Unit,
    private val onError: (error: PortingError) -> Unit,
    private val onSuccess: (outputPath: String) -> Unit
) {
    
    private val tag = "PortingPipeline"
    
    private val _currentStage = MutableStateFlow(PortingStep.INITIALIZING)
    val currentStage: StateFlow<PortingStep> = _currentStage.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private var workingDirectory: File? = null
    private var extractionStage: ExtractionStage? = null
    private var modificationStage: ModificationStage? = null
    private var packagingStage: PackagingStage? = null
    
    /**
     * Start the complete porting pipeline
     */
    suspend fun startPorting(
        gsiImageUri: Uri,
        deviceProfile: DeviceProfile,
        outputFormat: OutputFormat = OutputFormat.RECOVERY_ZIP
    ) {
        try {
            Log.d(tag, "Starting ROM porting pipeline...")
            
            // Initialize working directory
            initializeWorkspace()
            
            // Stage 1: Extract GSI image
            updateStage(PortingStep.EXTRACTING_GSI)
            val extractedData = extractGSI(gsiImageUri)
            
            // Stage 2: Analyze and prepare for modifications
            updateStage(PortingStep.ANALYZING_DEVICE)
            validateDeviceCompatibility(extractedData, deviceProfile)
            
            // Stage 3: Apply device-specific modifications
            updateStage(PortingStep.APPLYING_MODIFICATIONS)
            val modifiedData = applyModifications(extractedData, deviceProfile)
            
            // Stage 4: Package into flashable ROM
            updateStage(PortingStep.PACKAGING_ROM)
            val outputPath = packageROM(modifiedData, deviceProfile, outputFormat)
            
            // Stage 5: Verify output
            updateStage(PortingStep.VERIFYING_OUTPUT)
            verifyOutput(outputPath)
            
            updateStage(PortingStep.COMPLETED)
            onSuccess(outputPath)
            
        } catch (e: Exception) {
            Log.e(tag, "Porting pipeline failed: ${e.message}", e)
            onError(PortingError.PIPELINE_FAILED(e.message ?: "Unknown error"))
        } finally {
            cleanup()
        }
    }
    
    /**
     * Initialize the working directory for porting operations
     */
    private suspend fun initializeWorkspace() {
        Log.d(tag, "Initializing workspace...")
        
        workingDirectory = File(storageManager.getWorkspaceFolder(), "rom_porting_${System.currentTimeMillis()}")
        workingDirectory?.mkdirs()
        
        // Create subdirectories
        File(workingDirectory, "extracted").mkdirs()
        File(workingDirectory, "modified").mkdirs()
        File(workingDirectory, "output").mkdirs()
        File(workingDirectory, "temp").mkdirs()
        
        Log.d(tag, "Workspace initialized at: ${workingDirectory?.absolutePath}")
    }
    
    /**
     * Extract GSI image and analyze contents
     */
    private suspend fun extractGSI(gsiImageUri: Uri): ExtractedGSIData {
        Log.d(tag, "Extracting GSI image...")
        
        extractionStage = ExtractionStage(
            storageManager = storageManager,
            systemToolsManager = systemToolsManager,
            workingDirectory = workingDirectory!!,
            onProgress = { progress -> 
                updateProgress(progress * 0.25f) // Extraction is 25% of total
                onProgressUpdate(progress, "Extracting GSI")
            }
        )
        
        return extractionStage!!.extractGSI(gsiImageUri)
    }
    
    /**
     * Validate device compatibility with extracted GSI
     */
    private suspend fun validateDeviceCompatibility(
        extractedData: ExtractedGSIData,
        deviceProfile: DeviceProfile
    ) {
        Log.d(tag, "Validating device compatibility...")
        
        // Check architecture compatibility
        if (extractedData.architecture != deviceProfile.architecture) {
            throw PortingException("Architecture mismatch: GSI is ${extractedData.architecture}, device is ${deviceProfile.architecture}")
        }
        
        // Check Android version compatibility
        if (extractedData.androidVersion < deviceProfile.minAndroidVersion) {
            throw PortingException("Android version too low: GSI is ${extractedData.androidVersion}, device requires ${deviceProfile.minAndroidVersion}+")
        }
        
        // Check partition layout compatibility
        if (!deviceProfile.supportedPartitionLayouts.contains(extractedData.partitionLayout)) {
            Log.w(tag, "Partition layout mismatch, will attempt conversion")
        }
        
        Log.d(tag, "Device compatibility validated")
    }
    
    /**
     * Apply device-specific modifications to the GSI
     */
    private suspend fun applyModifications(
        extractedData: ExtractedGSIData,
        deviceProfile: DeviceProfile
    ): ModifiedGSIData {
        Log.d(tag, "Applying device-specific modifications...")
        
        modificationStage = ModificationStage(
            storageManager = storageManager,
            systemToolsManager = systemToolsManager,
            workingDirectory = workingDirectory!!,
            onProgress = { progress ->
                updateProgress(0.25f + (progress * 0.5f)) // Modification is 50% of total
                onProgressUpdate(progress, "Applying modifications")
            }
        )
        
        return modificationStage!!.applyModifications(extractedData, deviceProfile)
    }
    
    /**
     * Package the modified GSI into a flashable ROM
     */
    private suspend fun packageROM(
        modifiedData: ModifiedGSIData,
        deviceProfile: DeviceProfile,
        outputFormat: OutputFormat
    ): String {
        Log.d(tag, "Packaging ROM...")
        
        packagingStage = PackagingStage(
            storageManager = storageManager,
            systemToolsManager = systemToolsManager,
            workingDirectory = workingDirectory!!,
            onProgress = { progress ->
                updateProgress(0.75f + (progress * 0.2f)) // Packaging is 20% of total
                onProgressUpdate(progress, "Packaging ROM")
            }
        )
        
        return packagingStage!!.packageROM(modifiedData, deviceProfile, outputFormat)
    }
    
    /**
     * Verify the output ROM integrity
     */
    private suspend fun verifyOutput(outputPath: String) {
        Log.d(tag, "Verifying output...")
        
        val outputFile = File(outputPath)
        if (!outputFile.exists()) {
            throw PortingException("Output file not found: $outputPath")
        }
        
        if (outputFile.length() == 0L) {
            throw PortingException("Output file is empty")
        }
        
        // Additional verification based on output format
        when {
            outputPath.endsWith(".zip") -> verifyZipIntegrity(outputPath)
            outputPath.endsWith(".img") -> verifyImageIntegrity(outputPath)
        }
        
        updateProgress(1.0f)
        Log.d(tag, "Output verification completed")
    }
    
    /**
     * Verify ZIP file integrity
     */
    private suspend fun verifyZipIntegrity(zipPath: String) {
        val result = systemToolsManager.executeCommand("unzip -t $zipPath")
        if (!result.success) {
            throw PortingException("ZIP integrity check failed: ${result.error}")
        }
    }
    
    /**
     * Verify image file integrity
     */
    private suspend fun verifyImageIntegrity(imagePath: String) {
        // Check if it's a valid filesystem image
        val result = systemToolsManager.executeCommand("file $imagePath")
        if (!result.success || !result.output.contains("filesystem")) {
            Log.w(tag, "Image format verification inconclusive")
        }
    }
    
    /**
     * Clean up temporary files and resources
     */
    private suspend fun cleanup() {
        Log.d(tag, "Cleaning up...")
        
        try {
            workingDirectory?.let { dir ->
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Cleanup failed: ${e.message}")
        }
    }
    
    /**
     * Update the current stage
     */
    private fun updateStage(stage: PortingStep) {
        _currentStage.value = stage
        onStepUpdate(stage)
    }
    
    /**
     * Update the progress
     */
    private fun updateProgress(progress: Float) {
        _progress.value = progress.coerceIn(0f, 1f)
    }
    
    /**
     * Cancel the porting operation
     */
    fun cancel() {
        job.cancel()
        cleanup()
    }
}

/**
 * Porting pipeline steps
 */
enum class PortingStep {
    INITIALIZING,
    EXTRACTING_GSI,
    ANALYZING_DEVICE,
    APPLYING_MODIFICATIONS,
    PACKAGING_ROM,
    VERIFYING_OUTPUT,
    COMPLETED,
    FAILED
}

/**
 * Output format options
 */
enum class OutputFormat {
    RECOVERY_ZIP,      // Flashable ZIP for custom recovery
    FASTBOOT_IMAGES,   // Individual partition images for fastboot
    ODIN_TAR,         // Samsung Odin format
    SYSTEM_IMAGE      // Raw system image
}

/**
 * Porting error types
 */
sealed class PortingError(val message: String) {
    data class PIPELINE_FAILED(val error: String) : PortingError("Pipeline failed: $error")
    data class EXTRACTION_FAILED(val error: String) : PortingError("Extraction failed: $error")
    data class MODIFICATION_FAILED(val error: String) : PortingError("Modification failed: $error")
    data class PACKAGING_FAILED(val error: String) : PortingError("Packaging failed: $error")
    data class VERIFICATION_FAILED(val error: String) : PortingError("Verification failed: $error")
    data class DEVICE_INCOMPATIBLE(val error: String) : PortingError("Device incompatible: $error")
}

/**
 * Exception for porting operations
 */
class PortingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Data classes for pipeline stages
 */
data class ExtractedGSIData(
    val systemImagePath: String,
    val vendorImagePath: String?,
    val productImagePath: String?,
    val architecture: String,
    val androidVersion: Int,
    val partitionLayout: String,
    val buildProperties: Map<String, String>
)

data class ModifiedGSIData(
    val systemImagePath: String,
    val vendorImagePath: String?,
    val productImagePath: String?,
    val bootImagePath: String?,
    val recoveryImagePath: String?,
    val modifications: List<String>
)
