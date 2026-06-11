package com.yue.browser.data.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.AllowOrDeny
import com.yue.browser.domain.engine.BrowserEngine
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository

class GeckoViewEngine(
    private val settingsRepository: SettingsRepository
) : BrowserEngine {

    companion object {
        private const val TAG = "GeckoViewEngine"

        @Volatile
        internal var geckoRuntimeInstance: GeckoRuntime? = null
            private set

        private val activeSessions = java.util.Collections.synchronizedList(mutableListOf<GeckoSession>())
        val readyExtensions = java.util.concurrent.ConcurrentHashMap<String, WebExtension>()

        fun attachSessionDelegates(session: GeckoSession, extension: WebExtension) {
            session.webExtensionController.setTabDelegate(extension, object : WebExtension.SessionTabDelegate {})
            session.webExtensionController.setMessageDelegate(extension, object : WebExtension.MessageDelegate {
                override fun onMessage(
                    nativeApp: String,
                    message: Any,
                    sender: WebExtension.MessageSender
                ): GeckoResult<Any>? {
                    Log.d("GeckoViewEngine", "onMessage from session: $nativeApp")
                    return null
                }
            }, "yue-browser")
        }

        fun registerSession(session: GeckoSession) {
            activeSessions.add(session)
            for (ext in readyExtensions.values) {
                attachSessionDelegates(session, ext)
            }
        }

        fun unregisterSession(session: GeckoSession) {
            activeSessions.remove(session)
        }

        fun getRuntime(context: Context): GeckoRuntime {
            return geckoRuntimeInstance ?: synchronized(this) {
                geckoRuntimeInstance ?: run {
                    val settings = GeckoRuntimeSettings.Builder()
                        .javaScriptEnabled(true)
                        .webFontsEnabled(true)
                        .consoleOutput(true)
                        .aboutConfigEnabled(true)
                        .remoteDebuggingEnabled(true)
                        .build()
                    GeckoRuntime.create(context.applicationContext, settings).also { runtime ->
                        geckoRuntimeInstance = runtime
                        setupExtensionSupport(context.applicationContext, runtime)
                    }
                }
            }
        }
        
        fun setGlobalOnNewTabRequested(callback: (String) -> Unit) {
            globalOnNewTabRequested = callback
        }

        private var globalOnNewTabRequested: ((String) -> Unit)? = null

        /**
         * Setup extension support: PromptDelegate to auto-accept extension install prompts,
         * and AddonManagerDelegate to track extension lifecycle events.
         */
        private fun setupExtensionSupport(context: Context, runtime: GeckoRuntime) {
            // (Removed global TabDelegate from here, moved to onReady)

            // PromptDelegate — automatically accept extension install/update permissions
            runtime.webExtensionController.promptDelegate = object : WebExtensionController.PromptDelegate {
                override fun onUpdatePrompt(
                    currentlyInstalled: WebExtension,
                    updatedExtension: WebExtension,
                    newPermissions: Array<out String>,
                    newOrigins: Array<out String>
                ): GeckoResult<AllowOrDeny>? {
                    Log.i(TAG, "Extension update prompt for: ${updatedExtension.id} — auto-allowing")
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>
                ): GeckoResult<AllowOrDeny>? {
                    Log.i(TAG, "Extension optional prompt for: ${extension.id} — auto-allowing")
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }
            
            // Test ensureBuiltIn
            runtime.webExtensionController.ensureBuiltIn("resource://android/assets/dummy.xpi", "dummy@example.com").accept(
                { extension -> Log.i(TAG, "dummy loaded: ${extension?.id}") },
                { e -> Log.e(TAG, "dummy failed: $e", e) }
            )

            // AddonManagerDelegate — tracks extension readiness
            runtime.webExtensionController.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
                override fun onReady(extension: WebExtension) {
                    Log.i(TAG, "Extension READY: ${extension.id}")
                    
                    // Set TabDelegate to prevent extension crashes when they try to manage tabs
                    extension.setTabDelegate(object : WebExtension.TabDelegate {
                        override fun onNewTab(
                            source: WebExtension,
                            createDetails: WebExtension.CreateTabDetails
                        ): GeckoResult<GeckoSession>? {
                            Log.i(TAG, "Extension requested new tab: ${createDetails.url}")
                            var newSession: GeckoSession? = null
                            // Notify our global tab listener to create a tab
                            globalOnNewTabRequested?.invoke(createDetails.url ?: "about:blank")
                            // Note: we can't easily synchronously return the new GeckoSession here 
                            // because our architecture doesn't immediately return it. 
                            // But returning null is usually acceptable if we handle the intent.
                            return null
                        }

                        // Removed onCloseTab since it overrides nothing
                    })

                    readyExtensions[extension.id] = extension
                    for (session in activeSessions) {
                        attachSessionDelegates(session, extension)
                    }
                    
                    // Allow extension to work in private browsing
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                    
                    extension.setActionDelegate(object : WebExtension.ActionDelegate {
                        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
                            Log.d(TAG, "onBrowserAction for ${extension.id}")
                        }
                    })
                    
                    // Removed TabDelegate again
                }
            })

            // Log installed extensions on startup
            runtime.webExtensionController.list().accept(
                { extensions ->
                    Log.i(TAG, "Currently installed extensions: ${extensions?.size ?: 0}")
                    extensions?.forEach { ext ->
                        Log.i(TAG, "  - ${ext.id} enabled=${ext.metaData?.enabled}")
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to list extensions: ${error?.message}")
                }
            )

            // Restore saved extensions on startup
            try {
                val enabledAddons = com.yue.browser.data.repository.SettingsRepositoryImpl.instance.settingsFlow.value.enabledAddons
                GeckoExtensionManager.restoreExtensions(context, runtime, enabledAddons)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore extensions: ${e.message}")
            }
        }
    }


    override fun createSession(
        context: Context,
        id: String,
        isPrivate: Boolean,
        onLanguageDetected: ((String) -> Unit)?,
        onNewTabRequested: ((String) -> Unit)?
    ): BrowserSession {
        val runtime = getRuntime(context)
        return GeckoViewSession(context, id, isPrivate, runtime, settingsRepository, onLanguageDetected, onNewTabRequested)
    }

    override fun clearCache(context: Context) {
        geckoRuntimeInstance?.let { runtime ->
            runtime.storageController.clearData(
                org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
            )
        }
    }

    override fun clearCookies(context: Context) {
        geckoRuntimeInstance?.let { runtime ->
            runtime.storageController.clearData(
                org.mozilla.geckoview.StorageController.ClearFlags.COOKIES
            )
        }
    }
}
