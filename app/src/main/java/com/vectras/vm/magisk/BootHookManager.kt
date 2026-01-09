package com.vectras.vm.magisk

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * BootStage - VM boot stages
 */
enum class BootStage {
    PRE_INIT,      // Before any VM initialization
    PRE_BOOT,      // Before QEMU starts
    POST_BOOT,     // After QEMU starts
    POST_INIT,     // After VM is fully initialized
    SHUTDOWN       // During VM shutdown
}

/**
 * BootHook - Represents a boot hook script or callback
 */
interface BootHook {
    val id: String
    val stage: BootStage
    val priority: Int
    
    fun execute(context: Context, vmName: String): Boolean
}

/**
 * ScriptBootHook - Boot hook that executes a shell script
 */
class ScriptBootHook(
    override val id: String,
    override val stage: BootStage,
    override val priority: Int,
    private val scriptPath: String,
    private val privilegeLevel: PrivilegeLevel = PrivilegeLevel.NORMAL
) : BootHook {
    
    override fun execute(context: Context, vmName: String): Boolean {
        val script = File(scriptPath)
        if (!script.exists()) {
            Log.e("ScriptBootHook", "Script not found: $scriptPath")
            return false
        }
        
        // Make script executable
        script.setExecutable(true)
        
        // Execute script
        val command = "sh \"$scriptPath\" \"$vmName\""
        val result = PrivilegeManager.executeWithPrivilege(command, privilegeLevel)
        
        if (!result.isSuccess()) {
            Log.e("ScriptBootHook", "Script execution failed: ${result.error}")
            return false
        }
        
        Log.d("ScriptBootHook", "Script executed successfully: $scriptPath")
        return true
    }
}

/**
 * CallbackBootHook - Boot hook that executes a Kotlin callback
 */
class CallbackBootHook(
    override val id: String,
    override val stage: BootStage,
    override val priority: Int,
    private val callback: (Context, String) -> Boolean
) : BootHook {
    
    override fun execute(context: Context, vmName: String): Boolean {
        return try {
            callback(context, vmName)
        } catch (e: Exception) {
            Log.e("CallbackBootHook", "Callback execution failed", e)
            false
        }
    }
}

/**
 * BootHookManager - Manages VM boot hooks
 * 
 * Inspired by Magisk's boot script system for running custom scripts
 * at various stages of the VM lifecycle
 */
object BootHookManager {
    
    private const val TAG = "BootHookManager"
    
    private val hooks = ConcurrentHashMap<String, MutableList<BootHook>>()
    
    init {
        // Initialize hook lists for each stage
        BootStage.values().forEach { stage ->
            hooks[stage.name] = mutableListOf()
        }
    }
    
    /**
     * Register a boot hook
     */
    fun registerHook(hook: BootHook) {
        val stageHooks = hooks[hook.stage.name]
        if (stageHooks == null) {
            Log.e(TAG, "Invalid boot stage: ${hook.stage}")
            return
        }
        
        // Remove existing hook with same ID
        stageHooks.removeAll { it.id == hook.id }
        
        // Add new hook
        stageHooks.add(hook)
        
        // Sort by priority (higher first)
        stageHooks.sortByDescending { it.priority }
        
        Log.i(TAG, "Registered hook: ${hook.id} for stage ${hook.stage}")
    }
    
    /**
     * Unregister a boot hook
     */
    fun unregisterHook(hookId: String, stage: BootStage) {
        val stageHooks = hooks[stage.name]
        stageHooks?.removeAll { it.id == hookId }
        
        Log.i(TAG, "Unregistered hook: $hookId from stage $stage")
    }
    
    /**
     * Execute all hooks for a specific stage
     */
    fun executeStage(stage: BootStage, context: Context, vmName: String) {
        Log.i(TAG, "Executing boot stage: $stage for VM: $vmName")
        
        val stageHooks = hooks[stage.name] ?: return
        
        for (hook in stageHooks) {
            try {
                Log.d(TAG, "Executing hook: ${hook.id}")
                val success = hook.execute(context, vmName)
                
                if (!success) {
                    Log.w(TAG, "Hook execution failed: ${hook.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hook execution error: ${hook.id}", e)
            }
        }
        
        Log.i(TAG, "Completed boot stage: $stage")
    }
    
    /**
     * Load hooks from directory
     */
    fun loadHooksFromDirectory(context: Context, stage: BootStage) {
        val hooksDir = File(context.filesDir, "hooks/${stage.name.lowercase()}")
        if (!hooksDir.exists()) {
            return
        }
        
        val scripts = hooksDir.listFiles { file ->
            file.isFile && (file.extension == "sh" || file.name.endsWith(".hook"))
        }
        
        scripts?.forEach { script ->
            val hook = ScriptBootHook(
                id = script.nameWithoutExtension,
                stage = stage,
                priority = 50,
                scriptPath = script.absolutePath
            )
            
            registerHook(hook)
        }
        
        Log.i(TAG, "Loaded ${scripts?.size ?: 0} script hooks for stage $stage")
    }
    
    /**
     * Get all hooks for a stage
     */
    fun getHooks(stage: BootStage): List<BootHook> {
        return hooks[stage.name]?.toList() ?: emptyList()
    }
    
    /**
     * Clear all hooks for a stage
     */
    fun clearStage(stage: BootStage) {
        hooks[stage.name]?.clear()
        Log.i(TAG, "Cleared all hooks for stage: $stage")
    }
    
    /**
     * Clear all hooks
     */
    fun clearAll() {
        hooks.values.forEach { it.clear() }
        Log.i(TAG, "Cleared all boot hooks")
    }
    
    /**
     * Initialize hook directories
     */
    fun initHookDirectories(context: Context) {
        BootStage.values().forEach { stage ->
            val hooksDir = File(context.filesDir, "hooks/${stage.name.lowercase()}")
            if (!hooksDir.exists()) {
                hooksDir.mkdirs()
                Log.d(TAG, "Created hooks directory: ${hooksDir.absolutePath}")
            }
        }
    }
}
