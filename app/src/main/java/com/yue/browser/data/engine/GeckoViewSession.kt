package com.yue.browser.data.engine

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.AllowOrDeny
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.yue.browser.domain.engine.BrowserSession
import com.yue.browser.domain.repository.SettingsRepository

class GeckoViewSession(
    private val context: Context,
    override val id: String,
    override val isPrivate: Boolean,
    private val geckoRuntime: GeckoRuntime,
    private val settingsRepository: SettingsRepository,
    private val onLanguageDetected: ((String) -> Unit)? = null,
    private val onNewTabRequested: ((String) -> Unit)? = null
) : BrowserSession {

    private val geckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .allowJavascript(true)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
    )

    // Reusable GeckoView — created once and reused
    private val geckoViewInstance: GeckoView by lazy {
        GeckoView(context).also {
            it.setSession(geckoSession)
        }
    }

    override var url: String = if (isPrivate) "yue://newtab" else ""
        private set

    override var title: String = "New Tab"
        private set

    override var progress: Int = 0
        private set

    override var canGoBack: Boolean = false
        private set

    override var canGoForward: Boolean = false
        private set

    override var stateCallback: ((url: String, title: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    override var newTabCallback: ((url: String, isPrivate: Boolean) -> Unit)? = null
    override var faviconCallback: ((android.graphics.Bitmap) -> Unit)? = null
    override var thumbnailCaptureCallback: ((android.graphics.Bitmap) -> Unit)? = null

    private var currentScrollY by mutableStateOf(0)

    override val view: View
        get() = geckoViewInstance

    init {
        geckoSession.open(geckoRuntime)

        // Register this session for extensions — allows content scripts to run
        geckoSession.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                currentScrollY = scrollY
            }
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            settingsRepository.settingsFlow.collect { settings ->
                // Always enable tracking protection for ad blocking and judol protection
                val useTrackingProtection = true
                geckoSession.settings.useTrackingProtection = useTrackingProtection
            }
        }

        GeckoViewEngine.registerSession(geckoSession)

        // Progress delegate — page load tracking
        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, p: Int) {
                progress = p
                triggerCallback()
            }
            override fun onPageStart(session: GeckoSession, u: String) {
                progress = 0
                url = u
                triggerCallback()
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progress = 100
                triggerCallback()
            }
        }

        // Permission delegate — auto-allow permissions to prevent extension crashes
        geckoSession.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            }
        }

        // Navigation delegate — back/forward state, URL updates
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canBack: Boolean) {
                canGoBack = canBack
                triggerCallback()
            }
            override fun onCanGoForward(session: GeckoSession, canForward: Boolean) {
                canGoForward = canForward
                triggerCallback()
            }
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                // Intercept extension installation links
                if (uri.endsWith(".xpi") || uri.contains("/addon/") && uri.contains("/file/")) {
                    Log.i("GeckoViewSession", "Intercepted .xpi download: $uri")
                    try {
                        val runtime = GeckoViewEngine.geckoRuntimeInstance
                        if (runtime != null) {
                            runtime.webExtensionController.install(uri).accept(
                                { extension ->
                                    if (extension != null) {
                                        Log.i("GeckoViewSession", "Market install success: ${extension.id}")
                                        // Save to our preferences so it gets restored on next launch and shows in UI
                                        val settingsRepo = com.yue.browser.data.repository.SettingsRepositoryImpl.instance
                                        
                                        settingsRepo.saveAddonMetadata(
                                            addonId = extension.id,
                                            name = extension.metaData?.name ?: extension.id,
                                            version = extension.metaData?.version ?: "1.0.0",
                                            author = extension.metaData?.creatorName ?: "Unknown",
                                            description = extension.metaData?.description ?: ""
                                        )
                                        settingsRepo.setAddonEnabled(extension.id, true)
                                        
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            android.widget.Toast.makeText(geckoViewInstance.context, "Extension Installed: ${extension.metaData?.name ?: extension.id}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                { error ->
                                    Log.e("GeckoViewSession", "Market install failed: ${error?.message}")
                                }
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("GeckoViewSession", "Exception in market install: ${e.message}")
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
            override fun onLocationChange(
                session: GeckoSession,
                u: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (u != null) {
                    url = u
                    triggerCallback()
                }
            }
        }

        // Content delegate — title updates
        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, t: String?) {
                title = t ?: "New Tab"
                triggerCallback()
            }
        }
    }

    private fun triggerCallback() {
        stateCallback?.invoke(url, title, progress, canGoBack, canGoForward)
    }

    override fun loadUrl(url: String) {
        if (url == "yue://newtab") {
            this.url = url
            this.title = "New Tab"
            progress = 100
            triggerCallback()
            return
        }
        geckoSession.loadUri(url)
    }

    override fun goBack() {
        geckoSession.goBack()
    }

    override fun goForward() {
        geckoSession.goForward()
    }

    override fun reload() {
        geckoSession.reload()
    }

    override fun destroy() {
        try {
            GeckoViewEngine.unregisterSession(geckoSession)
            geckoSession.close()
        } catch (_: Exception) { }
    }

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        geckoSession.loadUri("javascript:void($script)")
        callback?.invoke(null)
    }

    override fun setJavaScriptEnabled(enabled: Boolean) {
        geckoSession.settings.allowJavascript = enabled
    }

    override fun setForceDarkMode(enabled: Boolean) {
        // GeckoView doesn't have a direct force dark mode API.
        // Could inject CSS if needed in the future.
    }

    override fun setDesktopModeEnabled(enabled: Boolean) {
        val mode = if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        geckoSession.settings.userAgentMode = mode
        val viewport = if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        geckoSession.settings.viewportMode = viewport
    }

    override fun captureThumbnail(callback: (Bitmap) -> Unit) {
        try {
            val gv = geckoViewInstance
            val width = gv.width.coerceAtLeast(1)
            val height = gv.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            gv.draw(canvas)
            callback(bitmap)
        } catch (_: Exception) { }
    }

    override fun startElementPicker(onElementPicked: (cssSelector: String) -> Unit) {
        // GeckoView: stub, not fully supported
    }

    override fun stopElementPicker() {
        // GeckoView: stub
    }

    @Composable
    override fun Render(
        modifier: Modifier,
        onScrollChanged: (visible: Boolean) -> Unit,
        onReload: () -> Unit,
        isGone: Boolean
    ) {
        val currentOnScrollChanged by rememberUpdatedState(onScrollChanged)

        AndroidView(
            factory = { ctx ->
                androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                    val gv = geckoViewInstance
                    // Detach from any previous parent
                    (gv.parent as? ViewGroup)?.removeView(gv)

                    // Bottom padding for toolbar
                    // val density = ctx.resources.displayMetrics.density
                    // val paddingPx = (80 * density).toInt()
                    // gv.clipToPadding = false
                    // gv.setPadding(0, 0, 0, paddingPx)

                    addView(gv, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))

                    setOnRefreshListener {
                        onReload()
                    }
                    setOnChildScrollUpCallback { _, _ -> currentScrollY > 0 }
                    isEnabled = (currentScrollY == 0)
                }
            },
            update = { swipeRefreshLayout ->
                if (progress >= 100 && swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }

                val targetVisibility = if (isGone) View.GONE else View.VISIBLE
                if (swipeRefreshLayout.visibility != targetVisibility) {
                    swipeRefreshLayout.visibility = targetVisibility
                }
                
                val targetEnabled = (currentScrollY == 0)
                if (swipeRefreshLayout.isEnabled != targetEnabled) {
                    swipeRefreshLayout.isEnabled = targetEnabled
                }
            },
            modifier = modifier
        )
    }
}
