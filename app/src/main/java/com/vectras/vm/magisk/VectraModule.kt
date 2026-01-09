package com.vectras.vm.magisk

import android.content.Context
import java.io.File

/**
 * VectraModule - Magisk-inspired module system for Vectras VM
 * 
 * Modules are self-contained extensions that can:
 * - Modify VM behavior
 * - Add new features
 * - Provide custom boot scripts
 * - Mount overlay filesystems
 */
abstract class VectraModule {
    
    /**
     * Module metadata
     */
    abstract val id: String
    abstract val name: String
    abstract val version: String
    abstract val author: String
    abstract val description: String
    
    /**
     * Module priority (higher = executed first)
     * Default: 50
     */
    open val priority: Int = 50
    
    /**
     * Minimum Vectras VM version required
     */
    open val minVectrasVersion: String = "3.0.0"
    
    /**
     * Module state
     */
    var isEnabled: Boolean = false
        internal set
    
    var isLoaded: Boolean = false
        internal set
    
    /**
     * Module lifecycle callbacks
     */
    
    /**
     * Called when module is loaded
     */
    open fun onLoad(context: Context) {
        // Override to perform initialization
    }
    
    /**
     * Called before VM starts (pre-boot hook)
     */
    open fun onPreBoot(context: Context, vmName: String) {
        // Override to run pre-boot operations
    }
    
    /**
     * Called after VM starts (post-boot hook)
     */
    open fun onPostBoot(context: Context, vmName: String) {
        // Override to run post-boot operations
    }
    
    /**
     * Called when module is unloaded
     */
    open fun onUnload(context: Context) {
        // Override to perform cleanup
    }
    
    /**
     * Get module directory
     */
    fun getModuleDir(context: Context): File {
        return File(context.filesDir, "modules/$id")
    }
    
    /**
     * Get module configuration file
     */
    fun getConfigFile(context: Context): File {
        return File(getModuleDir(context), "module.conf")
    }
    
    /**
     * Check if module is compatible with current Vectras version
     */
    fun isCompatible(currentVersion: String): Boolean {
        return compareVersions(currentVersion, minVectrasVersion) >= 0
    }
    
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            
            if (p1 != p2) return p1 - p2
        }
        
        return 0
    }
    
    override fun toString(): String {
        return "VectraModule(id='$id', name='$name', version='$version', enabled=$isEnabled)"
    }
}

/**
 * Module metadata container
 */
data class ModuleMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val priority: Int = 50,
    val minVectrasVersion: String = "3.0.0",
    val enabled: Boolean = false
)
