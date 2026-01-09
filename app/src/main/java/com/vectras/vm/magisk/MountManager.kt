package com.vectras.vm.magisk

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * MountPoint - Represents a mounted filesystem or overlay
 */
data class MountPoint(
    val source: String,
    val target: String,
    val type: MountType,
    val options: List<String> = emptyList(),
    val isMounted: Boolean = false
)

/**
 * MountType - Types of mounts supported
 */
enum class MountType {
    BIND,       // Bind mount (like Magisk's systemless mounts)
    OVERLAY,    // Overlay filesystem
    LOOP,       // Loop device
    TMPFS,      // Temporary filesystem
    STANDARD    // Standard mount
}

/**
 * MountManager - Magisk-inspired mount overlay system
 * 
 * Manages filesystem mounts and overlays for VM environments
 * Inspired by Magisk's systemless modification approach
 */
object MountManager {
    
    private const val TAG = "MountManager"
    
    private val mountPoints = ConcurrentHashMap<String, MountPoint>()
    
    /**
     * Create a bind mount (systemless modification)
     */
    fun createBindMount(
        source: String,
        target: String,
        readOnly: Boolean = false
    ): Boolean {
        if (!PrivilegeManager.hasPrivilege(PrivilegeLevel.ELEVATED)) {
            Log.w(TAG, "Bind mount requires elevated privileges")
            return false
        }
        
        // Verify source exists
        if (!File(source).exists()) {
            Log.e(TAG, "Source does not exist: $source")
            return false
        }
        
        // Create target directory if needed
        val targetFile = File(target)
        if (!targetFile.exists()) {
            targetFile.parentFile?.mkdirs()
            
            // Create empty file or directory based on source
            if (File(source).isDirectory) {
                targetFile.mkdirs()
            } else {
                targetFile.createNewFile()
            }
        }
        
        // Build mount command
        val options = if (readOnly) "ro,bind" else "bind"
        val command = "mount -o $options \"$source\" \"$target\""
        
        val result = PrivilegeManager.executeWithPrivilege(command, PrivilegeLevel.ELEVATED)
        
        if (result.isSuccess()) {
            val mountPoint = MountPoint(
                source = source,
                target = target,
                type = MountType.BIND,
                options = listOf(options),
                isMounted = true
            )
            
            mountPoints[target] = mountPoint
            Log.i(TAG, "Created bind mount: $source -> $target")
            return true
        } else {
            Log.e(TAG, "Failed to create bind mount: ${result.error}")
            return false
        }
    }
    
    /**
     * Create an overlay mount
     */
    fun createOverlayMount(
        lowerDir: String,
        upperDir: String,
        workDir: String,
        target: String
    ): Boolean {
        if (!PrivilegeManager.hasPrivilege(PrivilegeLevel.ELEVATED)) {
            Log.w(TAG, "Overlay mount requires elevated privileges")
            return false
        }
        
        // Create necessary directories
        File(upperDir).mkdirs()
        File(workDir).mkdirs()
        File(target).mkdirs()
        
        // Build overlay mount command
        val options = "lowerdir=$lowerDir,upperdir=$upperDir,workdir=$workDir"
        val command = "mount -t overlay overlay -o $options \"$target\""
        
        val result = PrivilegeManager.executeWithPrivilege(command, PrivilegeLevel.ELEVATED)
        
        if (result.isSuccess()) {
            val mountPoint = MountPoint(
                source = "$lowerDir:$upperDir",
                target = target,
                type = MountType.OVERLAY,
                options = listOf(options),
                isMounted = true
            )
            
            mountPoints[target] = mountPoint
            Log.i(TAG, "Created overlay mount: $target")
            return true
        } else {
            Log.e(TAG, "Failed to create overlay mount: ${result.error}")
            return false
        }
    }
    
    /**
     * Unmount a mount point
     */
    fun unmount(target: String, force: Boolean = false): Boolean {
        val mountPoint = mountPoints[target]
        if (mountPoint == null) {
            Log.w(TAG, "Mount point not found: $target")
            return false
        }
        
        if (!mountPoint.isMounted) {
            Log.w(TAG, "Mount point already unmounted: $target")
            return true
        }
        
        val forceFlag = if (force) " -f" else ""
        val command = "umount$forceFlag \"$target\""
        
        val result = PrivilegeManager.executeWithPrivilege(command, PrivilegeLevel.ELEVATED)
        
        if (result.isSuccess()) {
            mountPoints[target] = mountPoint.copy(isMounted = false)
            Log.i(TAG, "Unmounted: $target")
            return true
        } else {
            Log.e(TAG, "Failed to unmount: ${result.error}")
            return false
        }
    }
    
    /**
     * Get all mount points
     */
    fun getMountPoints(): List<MountPoint> {
        return mountPoints.values.toList()
    }
    
    /**
     * Check if a path is mounted
     */
    fun isMounted(target: String): Boolean {
        val mountPoint = mountPoints[target]
        return mountPoint?.isMounted == true
    }
    
    /**
     * Unmount all managed mount points
     */
    fun unmountAll(force: Boolean = false) {
        Log.i(TAG, "Unmounting all mount points")
        
        mountPoints.keys.forEach { target ->
            unmount(target, force)
        }
    }
    
    /**
     * Get system mount information
     */
    fun getSystemMounts(): List<String> {
        return try {
            File("/proc/mounts").readLines()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read system mounts", e)
            emptyList()
        }
    }
    
    /**
     * Check if overlay filesystem is supported
     */
    fun isOverlaySupported(): Boolean {
        return try {
            val mounts = File("/proc/filesystems").readLines()
            mounts.any { it.contains("overlay") }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check overlay support", e)
            false
        }
    }
}
