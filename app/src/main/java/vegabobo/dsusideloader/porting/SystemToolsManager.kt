package vegabobo.dsusideloader.porting

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages system tools detection and execution for ROM porting operations
 * Handles tool availability checking, command execution with progress tracking
 */
class SystemToolsManager {

    private val tag = "SystemToolsManager"

    // Essential tools for ROM porting
    private val requiredTools = mapOf(
        "dd" to "/system/bin/dd",
        "mount" to "/system/bin/mount",
        "umount" to "/system/bin/umount",
        "mke2fs" to "/system/bin/mke2fs",
        "resize2fs" to "/system/bin/resize2fs",
        "e2fsck" to "/system/bin/e2fsck",
        "tune2fs" to "/system/bin/tune2fs",
        "losetup" to "/system/bin/losetup",
        "mkfs.ext4" to "/system/bin/mkfs.ext4",
    )

    // Optional tools that enhance functionality
    private val optionalTools = mapOf(
        "fastboot" to "/system/bin/fastboot",
        "adb" to "/system/bin/adb",
        "zip" to "/system/bin/zip",
        "unzip" to "/system/bin/unzip",
        "tar" to "/system/bin/tar",
        "gzip" to "/system/bin/gzip",
        "gunzip" to "/system/bin/gunzip",
    )

    private val availableTools = mutableMapOf<String, String>()
    private val toolVersions = mutableMapOf<String, String?>()

    data class ToolInfo(
        val name: String,
        val path: String,
        val version: String?,
        val isAvailable: Boolean,
        val isRequired: Boolean,
    )

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String,
        val exitCode: Int,
    )

    /**
     * Initialize and detect available system tools
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        Log.d(tag, "Initializing system tools detection...")

        // Check required tools
        var allRequiredAvailable = true
        for ((tool, defaultPath) in requiredTools) {
            val toolPath = findToolPath(tool, defaultPath)
            if (toolPath != null) {
                availableTools[tool] = toolPath
                toolVersions[tool] = getToolVersion(tool, toolPath)
                Log.d(tag, "Found required tool: $tool at $toolPath")
            } else {
                Log.w(tag, "Required tool not found: $tool")
                allRequiredAvailable = false
            }
        }

        // Check optional tools
        for ((tool, defaultPath) in optionalTools) {
            val toolPath = findToolPath(tool, defaultPath)
            if (toolPath != null) {
                availableTools[tool] = toolPath
                toolVersions[tool] = getToolVersion(tool, toolPath)
                Log.d(tag, "Found optional tool: $tool at $toolPath")
            } else {
                Log.d(tag, "Optional tool not found: $tool")
            }
        }

        Log.d(tag, "Tool detection complete. Required tools available: $allRequiredAvailable")
        return@withContext allRequiredAvailable
    }

    /**
     * Find the actual path of a tool, checking multiple locations
     */
    private suspend fun findToolPath(toolName: String, defaultPath: String): String? = withContext(Dispatchers.IO) {
        val searchPaths = listOf(
            defaultPath,
            "/system/bin/$toolName",
            "/system/xbin/$toolName",
            "/vendor/bin/$toolName",
            "/sbin/$toolName",
            "/bin/$toolName",
            "/usr/bin/$toolName",
        )

        for (path in searchPaths) {
            if (File(path).exists() && isExecutable(path)) {
                return@withContext path
            }
        }

        // Try using 'which' command as fallback
        val result = executeCommand("which $toolName", timeout = 5000)
        if (result.success && result.output.isNotBlank()) {
            val foundPath = result.output.trim()
            if (File(foundPath).exists()) {
                return@withContext foundPath
            }
        }

        return@withContext null
    }

    /**
     * Check if a file is executable
     */
    private suspend fun isExecutable(path: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("test -x $path", timeout = 2000)
        return@withContext result.success
    }

    /**
     * Get version information for a tool
     */
    private suspend fun getToolVersion(toolName: String, toolPath: String): String? = withContext(Dispatchers.IO) {
        val versionCommands = listOf(
            "$toolPath --version",
            "$toolPath -V",
            "$toolPath -version",
        )

        for (cmd in versionCommands) {
            val result = executeCommand(cmd, timeout = 5000)
            if (result.success && result.output.isNotBlank()) {
                return@withContext result.output.lines().firstOrNull()?.trim()
            }
        }
        return@withContext null
    }

    /**
     * Execute a system command with timeout and error handling
     */
    suspend fun executeCommand(
        command: String,
        timeout: Long = 30000,
        onProgress: ((String) -> Unit)? = null,
    ): CommandResult = withContext(Dispatchers.IO) {
        Log.d(tag, "Executing command: $command")

        try {
            val shell = Shell.getShell()
            val result = shell.newJob()
                .add(command)
                .to(mutableListOf<String>(), mutableListOf<String>())
                .exec()

            val output = result.out.joinToString("\n")
            val error = result.err.joinToString("\n")
            val success = result.isSuccess

            Log.d(tag, "Command result - Success: $success, Exit code: ${result.code}")
            if (!success) {
                Log.w(tag, "Command error: $error")
            }

            return@withContext CommandResult(
                success = success,
                output = output,
                error = error,
                exitCode = result.code,
            )
        } catch (e: Exception) {
            Log.e(tag, "Command execution failed: ${e.message}", e)
            return@withContext CommandResult(
                success = false,
                output = "",
                error = e.message ?: "Unknown error",
                exitCode = -1,
            )
        }
    }

    /**
     * Get information about all detected tools
     */
    fun getToolsInfo(): List<ToolInfo> {
        val toolsInfo = mutableListOf<ToolInfo>()

        // Add required tools
        for ((tool, _) in requiredTools) {
            val path = availableTools[tool]
            toolsInfo.add(
                ToolInfo(
                    name = tool,
                    path = path ?: "Not found",
                    version = toolVersions[tool],
                    isAvailable = path != null,
                    isRequired = true,
                ),
            )
        }

        // Add optional tools
        for ((tool, _) in optionalTools) {
            val path = availableTools[tool]
            if (path != null) {
                toolsInfo.add(
                    ToolInfo(
                        name = tool,
                        path = path,
                        version = toolVersions[tool],
                        isAvailable = true,
                        isRequired = false,
                    ),
                )
            }
        }

        return toolsInfo
    }

    /**
     * Check if a specific tool is available
     */
    fun isToolAvailable(toolName: String): Boolean {
        return availableTools.containsKey(toolName)
    }

    /**
     * Get the path of a specific tool
     */
    fun getToolPath(toolName: String): String? {
        return availableTools[toolName]
    }

    /**
     * Check if all required tools are available
     */
    fun areRequiredToolsAvailable(): Boolean {
        return requiredTools.keys.all { availableTools.containsKey(it) }
    }

    /**
     * Create a loop device for image mounting
     */
    suspend fun createLoopDevice(imagePath: String): CommandResult {
        val command = "losetup -f --show $imagePath"
        return executeCommand(command)
    }

    /**
     * Remove a loop device
     */
    suspend fun removeLoopDevice(loopDevice: String): CommandResult {
        val command = "losetup -d $loopDevice"
        return executeCommand(command)
    }

    /**
     * Mount a filesystem
     */
    suspend fun mountFilesystem(
        device: String,
        mountPoint: String,
        fsType: String = "ext4",
        options: String = "",
    ): CommandResult {
        val optionsStr = if (options.isNotEmpty()) "-o $options" else ""
        val command = "mount -t $fsType $optionsStr $device $mountPoint"
        return executeCommand(command)
    }

    /**
     * Unmount a filesystem
     */
    suspend fun unmountFilesystem(mountPoint: String): CommandResult {
        val command = "umount $mountPoint"
        return executeCommand(command)
    }

    /**
     * Resize an ext4 filesystem
     */
    suspend fun resizeFilesystem(device: String, newSize: String? = null): CommandResult {
        val sizeParam = newSize?.let { " $it" } ?: ""
        val command = "resize2fs $device$sizeParam"
        return executeCommand(command)
    }

    /**
     * Check filesystem integrity
     */
    suspend fun checkFilesystem(device: String, autoFix: Boolean = false): CommandResult {
        val fixParam = if (autoFix) "-y" else "-n"
        val command = "e2fsck $fixParam $device"
        return executeCommand(command)
    }
}
