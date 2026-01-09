package com.vectras.vm.magisk

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * PrivilegeLevel - Defines privilege levels for operations
 */
enum class PrivilegeLevel {
    NORMAL,      // Standard user privileges
    ELEVATED,    // Requires root or special permissions
    SYSTEM       // System-level access
}

/**
 * PrivilegeManager - Magisk-inspired privilege management
 * 
 * Handles privilege escalation, permission checking, and secure operation execution
 */
object PrivilegeManager {
    
    private const val TAG = "PrivilegeManager"
    
    private var rootAvailable: Boolean? = null
    private var suPath: String? = null
    
    /**
     * Check if root access is available
     */
    fun isRootAvailable(): Boolean {
        rootAvailable?.let { return it }
        
        // Check common su binary locations
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su"
        )
        
        for (path in suPaths) {
            if (java.io.File(path).exists()) {
                // Try to execute su
                try {
                    val process = Runtime.getRuntime().exec(arrayOf(path, "-c", "id"))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = reader.readLine()
                    reader.close()
                    
                    val exitCode = process.waitFor()
                    
                    if (exitCode == 0 && output?.contains("uid=0") == true) {
                        rootAvailable = true
                        suPath = path
                        Log.i(TAG, "Root access available via: $path")
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to execute su at $path: ${e.message}")
                }
            }
        }
        
        rootAvailable = false
        Log.i(TAG, "Root access not available")
        return false
    }
    
    /**
     * Execute a command with elevated privileges
     */
    fun executeWithPrivilege(
        command: String,
        privilegeLevel: PrivilegeLevel = PrivilegeLevel.ELEVATED
    ): CommandResult {
        return when (privilegeLevel) {
            PrivilegeLevel.NORMAL -> executeNormal(command)
            PrivilegeLevel.ELEVATED -> executeElevated(command)
            PrivilegeLevel.SYSTEM -> executeSystem(command)
        }
    }
    
    /**
     * Execute command with normal privileges
     */
    private fun executeNormal(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val output = readOutput(process.inputStream)
            val error = readOutput(process.errorStream)
            val exitCode = process.waitFor()
            
            CommandResult(exitCode, output, error, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute normal command: $command", e)
            CommandResult(-1, "", e.message ?: "Unknown error", false)
        }
    }
    
    /**
     * Execute command with elevated privileges (root)
     */
    private fun executeElevated(command: String): CommandResult {
        if (!isRootAvailable()) {
            Log.w(TAG, "Elevated privileges requested but root not available")
            return CommandResult(-1, "", "Root access not available", false)
        }
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(suPath!!, "-c", command))
            val output = readOutput(process.inputStream)
            val error = readOutput(process.errorStream)
            val exitCode = process.waitFor()
            
            CommandResult(exitCode, output, error, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute elevated command: $command", e)
            CommandResult(-1, "", e.message ?: "Unknown error", false)
        }
    }
    
    /**
     * Execute command with system privileges
     */
    private fun executeSystem(command: String): CommandResult {
        // System-level execution (similar to elevated but with additional checks)
        return executeElevated(command)
    }
    
    /**
     * Check if a specific privilege level is available
     */
    fun hasPrivilege(level: PrivilegeLevel): Boolean {
        return when (level) {
            PrivilegeLevel.NORMAL -> true
            PrivilegeLevel.ELEVATED, PrivilegeLevel.SYSTEM -> isRootAvailable()
        }
    }
    
    /**
     * Request privilege escalation (placeholder for UI integration)
     */
    fun requestPrivilegeEscalation(context: Context, reason: String): Boolean {
        Log.i(TAG, "Privilege escalation requested: $reason")
        
        // In a full implementation, this would show a dialog to the user
        // For now, just check if root is available
        return isRootAvailable()
    }
    
    /**
     * Read output from input stream
     */
    private fun readOutput(inputStream: java.io.InputStream): String {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            reader.close()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Get the su binary path
     */
    fun getSuPath(): String? {
        isRootAvailable() // Ensure detection has run
        return suPath
    }
}

/**
 * Result of a privileged command execution
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val success: Boolean
) {
    fun isSuccess(): Boolean = success && exitCode == 0
}
