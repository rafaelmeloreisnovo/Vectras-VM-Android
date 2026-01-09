package com.vectras.vm.magisk

import android.content.Context
import android.util.Log
import com.vectras.vm.AppConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * ModuleManager - Magisk-inspired module management system
 * 
 * Manages loading, enabling, disabling, and execution of VectraModules
 */
object ModuleManager {
    
    private const val TAG = "ModuleManager"
    
    private val modules = ConcurrentHashMap<String, VectraModule>()
    private var initialized = false
    
    /**
     * Initialize the module manager
     */
    fun init(context: Context) {
        if (initialized) {
            Log.w(TAG, "ModuleManager already initialized")
            return
        }
        
        Log.i(TAG, "Initializing ModuleManager")
        
        // Create modules directory if it doesn't exist
        val modulesDir = getModulesDir(context)
        if (!modulesDir.exists()) {
            modulesDir.mkdirs()
            Log.d(TAG, "Created modules directory: ${modulesDir.absolutePath}")
        }
        
        // Scan for modules
        scanModules(context)
        
        // Load enabled modules
        loadEnabledModules(context)
        
        initialized = true
        Log.i(TAG, "ModuleManager initialized with ${modules.size} modules")
    }
    
    /**
     * Register a module
     */
    fun registerModule(module: VectraModule, context: Context): Boolean {
        if (modules.containsKey(module.id)) {
            Log.w(TAG, "Module ${module.id} already registered")
            return false
        }
        
        // Check compatibility
        if (!module.isCompatible(AppConfig.vectrasVersion)) {
            Log.e(TAG, "Module ${module.id} is incompatible with current version")
            return false
        }
        
        modules[module.id] = module
        Log.i(TAG, "Registered module: ${module.id}")
        
        // Create module directory
        val moduleDir = module.getModuleDir(context)
        if (!moduleDir.exists()) {
            moduleDir.mkdirs()
        }
        
        return true
    }
    
    /**
     * Enable a module
     */
    fun enableModule(moduleId: String, context: Context): Boolean {
        val module = modules[moduleId]
        if (module == null) {
            Log.e(TAG, "Module not found: $moduleId")
            return false
        }
        
        if (module.isEnabled) {
            Log.w(TAG, "Module already enabled: $moduleId")
            return true
        }
        
        try {
            module.onLoad(context)
            module.isEnabled = true
            module.isLoaded = true
            
            // Save enabled state
            saveModuleState(context, moduleId, true)
            
            Log.i(TAG, "Enabled module: $moduleId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable module: $moduleId", e)
            return false
        }
    }
    
    /**
     * Disable a module
     */
    fun disableModule(moduleId: String, context: Context): Boolean {
        val module = modules[moduleId]
        if (module == null) {
            Log.e(TAG, "Module not found: $moduleId")
            return false
        }
        
        if (!module.isEnabled) {
            Log.w(TAG, "Module already disabled: $moduleId")
            return true
        }
        
        try {
            module.onUnload(context)
            module.isEnabled = false
            module.isLoaded = false
            
            // Save disabled state
            saveModuleState(context, moduleId, false)
            
            Log.i(TAG, "Disabled module: $moduleId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable module: $moduleId", e)
            return false
        }
    }
    
    /**
     * Execute pre-boot hooks for all enabled modules
     */
    fun executePreBootHooks(context: Context, vmName: String) {
        Log.d(TAG, "Executing pre-boot hooks for VM: $vmName")
        
        val sortedModules = modules.values
            .filter { it.isEnabled }
            .sortedByDescending { it.priority }
        
        for (module in sortedModules) {
            try {
                Log.d(TAG, "Executing pre-boot hook: ${module.id}")
                module.onPreBoot(context, vmName)
            } catch (e: Exception) {
                Log.e(TAG, "Pre-boot hook failed for module: ${module.id}", e)
            }
        }
    }
    
    /**
     * Execute post-boot hooks for all enabled modules
     */
    fun executePostBootHooks(context: Context, vmName: String) {
        Log.d(TAG, "Executing post-boot hooks for VM: $vmName")
        
        val sortedModules = modules.values
            .filter { it.isEnabled }
            .sortedByDescending { it.priority }
        
        for (module in sortedModules) {
            try {
                Log.d(TAG, "Executing post-boot hook: ${module.id}")
                module.onPostBoot(context, vmName)
            } catch (e: Exception) {
                Log.e(TAG, "Post-boot hook failed for module: ${module.id}", e)
            }
        }
    }
    
    /**
     * Get all registered modules
     */
    fun getModules(): List<VectraModule> {
        return modules.values.toList()
    }
    
    /**
     * Get a specific module
     */
    fun getModule(moduleId: String): VectraModule? {
        return modules[moduleId]
    }
    
    /**
     * Get modules directory
     */
    private fun getModulesDir(context: Context): File {
        return File(context.filesDir, "modules")
    }
    
    /**
     * Scan for modules in the modules directory
     */
    private fun scanModules(context: Context) {
        // This is a placeholder for scanning external module files
        // In a full implementation, this would scan for module.prop files
        // and dynamically load module classes
        Log.d(TAG, "Scanning modules directory (placeholder)")
    }
    
    /**
     * Load enabled modules from saved state
     */
    private fun loadEnabledModules(context: Context) {
        val stateFile = File(context.filesDir, "modules_state.conf")
        if (!stateFile.exists()) {
            return
        }
        
        try {
            stateFile.readLines().forEach { line ->
                val parts = line.split("=")
                if (parts.size == 2) {
                    val moduleId = parts[0].trim()
                    val enabled = parts[1].trim().toBoolean()
                    
                    if (enabled) {
                        val module = modules[moduleId]
                        if (module != null) {
                            enableModule(moduleId, context)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load module state", e)
        }
    }
    
    /**
     * Save module state to file
     */
    private fun saveModuleState(context: Context, moduleId: String, enabled: Boolean) {
        val stateFile = File(context.filesDir, "modules_state.conf")
        
        try {
            val states = mutableMapOf<String, Boolean>()
            
            // Read existing states
            if (stateFile.exists()) {
                stateFile.readLines().forEach { line ->
                    val parts = line.split("=")
                    if (parts.size == 2) {
                        states[parts[0].trim()] = parts[1].trim().toBoolean()
                    }
                }
            }
            
            // Update state
            states[moduleId] = enabled
            
            // Write back
            stateFile.writeText(
                states.entries.joinToString("\n") { "${it.key}=${it.value}" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save module state", e)
        }
    }
    
    /**
     * Shutdown the module manager
     */
    fun shutdown(context: Context) {
        Log.i(TAG, "Shutting down ModuleManager")
        
        // Unload all modules
        modules.values.forEach { module ->
            if (module.isEnabled) {
                try {
                    module.onUnload(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unload module: ${module.id}", e)
                }
            }
        }
        
        modules.clear()
        initialized = false
    }
}
