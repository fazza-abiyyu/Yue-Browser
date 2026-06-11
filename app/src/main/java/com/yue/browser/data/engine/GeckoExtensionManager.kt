package com.yue.browser.data.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object GeckoExtensionManager {
    private const val TAG = "GeckoExtensionManager"

    // Map from our addon ID to the installed GeckoView extension ID
    private val installedExtensionMap = mutableMapOf<String, String>()
    private val currentlyEnabledExtensions = mutableSetOf<String>()
    private var isSyncing = false

    fun installExtension(context: Context, runtime: GeckoRuntime, xpiFile: File, enabledAddons: MutableSet<String>, addonId: String, callback: (Boolean, String?) -> Unit) {
        if (!xpiFile.exists()) {
            callback(false, "File not found")
            return
        }

        try {
            val httpUrl = LocalServer.start(xpiFile)
            Log.i(TAG, "Installing WebExtension: $addonId from $httpUrl")
            
            // Use ensureBuiltIn to bypass permission prompts and auto-grant all permissions
            // We MUST use install() for downloaded/local files. ensureBuiltIn() is only for APK-bundled assets 
            // and will cause "WebExtension context not found" if used on regular files.
            runtime.webExtensionController.install(httpUrl).accept(
                { extension ->
                    if (extension != null) {
                        Log.i(TAG, "Successfully installed extension: ${extension.id}")
                        installedExtensionMap[addonId] = extension.id
                        enabledAddons.add(addonId)
                        enabledAddons.add(extension.id)

                        // Explicitly enable the extension!
                        runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.APP)
                        currentlyEnabledExtensions.add(extension.id)

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "Extension Installed: ${extension.metaData?.name ?: extension.id}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        
                        callback(true, extension.id)
                    } else {
                        callback(false, "Extension object is null")
                    }
                },
                { exception ->
                    Log.e(TAG, "Failed to install extension: ${exception?.message}", exception)
                    callback(false, exception?.message)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during extension install: ${e.message}", e)
            callback(false, e.message)
        }
    }

    fun syncExtensions(runtime: GeckoRuntime, enabledAddons: Set<String>) {
        if (isSyncing) return
        isSyncing = true

        runtime.webExtensionController.list().accept(
            { installedExtensions ->
                isSyncing = false
                installedExtensions?.let { list ->
                    Log.i(TAG, "Syncing ${list.size} extensions, enabledAddons=$enabledAddons")
                    for (extension in list) {
                        val isEnabled = enabledAddons.any { addonId ->
                            installedExtensionMap[addonId] == extension.id || addonId == extension.id
                        }
                        Log.w(TAG, "syncExtensions: checking ${extension.id}. isEnabled=$isEnabled. enabledAddons=$enabledAddons, installedMap=$installedExtensionMap")
                        if (isEnabled) {
                            if (currentlyEnabledExtensions.add(extension.id)) {
                                Log.w(TAG, "syncExtensions: Enabling extension: ${extension.id}")
                                runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.APP)
                            }
                        } else {
                            if (currentlyEnabledExtensions.remove(extension.id)) {
                                Log.w(TAG, "syncExtensions: Disabling extension: ${extension.id} because it's not in enabledAddons!")
                                runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.APP)
                            } else {
                                Log.w(TAG, "syncExtensions: Wants to disable ${extension.id}, but it was not in currentlyEnabledExtensions!")
                                // FORCE DISABLE ANYWAY JUST TO BE SAFE? No, let's not force it.
                            }
                        }
                    }
                }
            },
            { exception ->
                isSyncing = false
                Log.e(TAG, "Failed to list extensions during sync: ${exception?.message}", exception)
            }
        )
    }

    /**
     * Re-installs/restores extensions from the downloaded directory on startup.
     */
    fun restoreExtensions(context: Context, runtime: GeckoRuntime, enabledAddons: Set<String>) {
        val extensionDir = File(context.filesDir, "extensions")
        if (!extensionDir.exists()) return

        val files = extensionDir.listFiles { _, name -> name.endsWith(".xpi") } ?: return
        
        for (xpiFile in files) {
            val addonId = xpiFile.nameWithoutExtension // e.g., "ublock_origin"

            try {
                val fileUrl = "file://${xpiFile.absolutePath}"
                Log.i(TAG, "Restoring WebExtension: $addonId from $fileUrl")
                
                // Use ensureBuiltIn to bypass permission prompts and auto-grant all permissions
                runtime.webExtensionController.install(fileUrl).accept(
                    { extension ->
                        if (extension != null) {
                            Log.i(TAG, "Successfully restored extension: ${extension.id} (addonId=$addonId)")
                            installedExtensionMap[addonId] = extension.id

                            // Add to currently enabled set since ensureBuiltIn enables it by default
                            currentlyEnabledExtensions.add(extension.id)
                        }
                    },
                    { exception ->
                        Log.e(TAG, "Failed to restore extension $addonId: ${exception?.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception restoring extension $addonId: ${e.message}")
            }
        }
    }
}
