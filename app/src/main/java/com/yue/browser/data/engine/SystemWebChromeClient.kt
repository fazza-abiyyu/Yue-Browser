package com.yue.browser.data.engine

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.view.View
import android.graphics.Bitmap
import com.yue.browser.data.adblock.AdBlockEngine
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.JsPromptResult
import java.util.concurrent.ConcurrentHashMap

/**
 * ### 1. Tab-based OAuth Popups (Integrated Theme)
 * - **Problem**: When OAuth popups (Google, Discord, etc.) were loaded inside a custom fullscreen Dialog container, it had hardcoded colors and a light grey header that clashed with the dark mode of the website and the browser's theme. Opening it in a standard separate tab historically broke the JS `window.opener` connection.
 * - **Solution**:
 *   - Overhauled popup handling to open popup `WebView` instances directly as **normal tabs** in Yue Browser's main tab layout.
 *   - Implemented `createNewTabWithWebView` in `TabRepositoryImpl` which creates a `SystemWebViewSession` that wraps the pre-existing `WebView` generated in `onCreateWindow`. This preserves the window hierarchy, JS `window.opener` references, and session state.
 *   - Overrode `onCloseWindow` to trigger `session.requestCloseCallback?.invoke()`, which automatically closes the tab when the website performs authentication completion and calls `window.close()`.
 *   - This guarantees that popups match Yue Browser's user interface, support dark mode simulation, and are fully integrated into tab management without any theme mismatch.
 */
class SystemWebChromeClient(
    private val context: android.content.Context,
    private val session: SystemWebViewSession,
    private val settingsRepository: com.yue.browser.domain.repository.SettingsRepository,
    private val isPrivate: Boolean
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    private var fullscreenContainer: FullscreenContainer? = null
    private var lockButton: android.view.View? = null
    private var speedupBadge: android.widget.TextView? = null
    private var isOrientationLocked = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    companion object {
        // Permission handler registry — maps requestCode -> handler
        private val permissionHandlers = ConcurrentHashMap<Int, (IntArray) -> Unit>()

        // File chooser pending state
        private var pendingFileChooser: android.webkit.ValueCallback<Array<android.net.Uri>?>? = null
        private var pendingFileChooserAcceptMultiple: Boolean = false
        private const val FILE_CHOOSER_REQUEST_CODE = 2001

        fun registerPermissionHandler(requestCode: Int, handler: (IntArray) -> Unit) {
            permissionHandlers[requestCode] = handler
        }

        fun unregisterPermissionHandler(requestCode: Int) {
            permissionHandlers.remove(requestCode)
        }

        /**
         * Dispatch system permission result to the matching ChromeClient handler.
         * Called from MainActivity.onRequestPermissionsResult.
         */
        fun dispatchPermissionResult(requestCode: Int, grantResults: IntArray) {
            android.util.Log.d("YueGeolocation", "dispatchPermissionResult requestCode=$requestCode grantResults=${grantResults.toList()} handlerRegistered=${permissionHandlers.containsKey(requestCode)}")
            val handler = permissionHandlers.remove(requestCode)
            if (handler != null) {
                android.util.Log.d("YueGeolocation", "handler found for requestCode=$requestCode — invoking")
                handler.invoke(grantResults)
            } else {
                android.util.Log.w("YueGeolocation", "NO handler for requestCode=$requestCode — location result lost!")
            }
        }

        /**
         * Dispatch file chooser result back to the pending onShowFileChooser callback.
         * Called from MainActivity.onActivityResult / ActivityResultLauncher.
         */
        fun dispatchFileChooserResult(resultCode: Int, data: android.content.Intent?) {
            val callback = pendingFileChooser ?: return
            pendingFileChooser = null

            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                val uris = mutableListOf<android.net.Uri>()
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        uris.add(clip.getItemAt(i).uri)
                    }
                } else {
                    data.data?.let { uris.add(it) }
                }
                if (uris.isNotEmpty()) {
                    callback.onReceiveValue(uris.toTypedArray())
                } else {
                    callback.onReceiveValue(null)
                }
            } else {
                callback.onReceiveValue(null)
            }
        }
    }

    // Pending permission state — saved while waiting for system permission dialog
    private var pendingPermissionRequest: android.webkit.PermissionRequest? = null
    private var pendingPermissionCamera: Boolean = false
    private var pendingPermissionMic: Boolean = false
    private var pendingPermissionHost: String = ""
    private var pendingLocationCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var pendingLocationOrigin: String = ""

    private fun showLockButton(button: android.view.View) {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        button.animate().cancel()
        if (button.visibility != android.view.View.VISIBLE) {
            button.alpha = 0f
            button.visibility = android.view.View.VISIBLE
        }
        button.animate()
            .alpha(1f)
            .setDuration(200)
            .start()

        val runnable = Runnable {
            hideLockButton(button)
        }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, 2000)
    }

    private fun hideLockButton(button: android.view.View) {
        button.animate().cancel()
        button.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { button.visibility = android.view.View.GONE }
            .start()
    }

    private fun showSpeedupBadge(rate: Float) {
        val badge = speedupBadge ?: return
        val formattedRate = String.format(java.util.Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.')
        val template = context.getString(com.yue.browser.R.string.video_speedup_indicator)
        val displayText = template.replace("%1\$s", formattedRate)
        val htmlText = "$displayText <font color='#EC4899'>&raquo;</font>"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            badge.text = android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            badge.text = android.text.Html.fromHtml(htmlText)
        }

        badge.animate().cancel()
        if (badge.visibility != android.view.View.VISIBLE) {
            badge.alpha = 0f
            badge.visibility = android.view.View.VISIBLE
        }
        badge.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideSpeedupBadge() {
        val badge = speedupBadge ?: return
        badge.animate().cancel()
        badge.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { badge.visibility = android.view.View.GONE }
            .start()
    }

    private fun findActivity(ctx: android.content.Context): android.app.Activity? {
        var current = ctx
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) {
                return current
            }
            current = current.baseContext
        }
        return current as? android.app.Activity
    }

    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
        if (request == null) return
        val context = context
        val activity = findActivity(context) ?: com.yue.browser.MainActivity.getActiveActivity()
        if (activity == null) {
            request.deny()
            return
        }

        val origin = request.origin.toString()
        val host = try { android.net.Uri.parse(origin).host ?: origin } catch (e: Exception) { origin }
        val cleanHost = host.removePrefix("www.").removePrefix("m.").lowercase()

        val resources = request.resources ?: emptyArray()
        val wantsCamera = resources.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val wantsMic = resources.contains(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val wantsProtectedMedia = resources.contains(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)

        if (wantsProtectedMedia && !wantsCamera && !wantsMic) {
            request.grant(arrayOf(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
            return
        }

        // Check site permissions settings
        val settings = settingsRepository.settingsFlow.value
        val siteMap = settings.sitePermissions[cleanHost] ?: emptyMap()

        val cameraGranted = siteMap["camera"]
        val micGranted = siteMap["microphone"]

        // Decide if we should allow, block, or ask.
        val needToAskCamera = wantsCamera && cameraGranted == null
        val needToAskMic = wantsMic && micGranted == null

        val isCameraBlocked = wantsCamera && cameraGranted == false
        val isMicBlocked = wantsMic && micGranted == false

        if (isCameraBlocked || isMicBlocked) {
            request.deny()
            return
        }

        if (needToAskCamera || needToAskMic) {
            // Show dialog to ask
            mainHandler.post {
                val message = StringBuilder("$cleanHost wants to access your:\n")
                if (needToAskCamera) message.append("- Camera\n")
                if (needToAskMic) message.append("- Microphone\n")

                android.app.AlertDialog.Builder(activity)
                    .setTitle("Site Permission Request")
                    .setMessage(message.toString().trim())
                    .setPositiveButton("Allow") { _, _ ->
                        if (wantsCamera) settingsRepository.setSitePermission(cleanHost, "camera", true)
                        if (wantsMic) settingsRepository.setSitePermission(cleanHost, "microphone", true)
                        checkAndRequestSystemPermissions(activity, request, wantsCamera, wantsMic)
                    }
                    .setNegativeButton("Block") { _, _ ->
                        if (wantsCamera) settingsRepository.setSitePermission(cleanHost, "camera", false)
                        if (wantsMic) settingsRepository.setSitePermission(cleanHost, "microphone", false)
                        request.deny()
                    }
                    .setNeutralButton("Cancel") { _, _ ->
                        request.deny()
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            val shouldGrantCamera = wantsCamera && cameraGranted == true
            val shouldGrantMic = wantsMic && micGranted == true
            checkAndRequestSystemPermissions(activity, request, shouldGrantCamera, shouldGrantMic)
        }
    }

    private fun checkAndRequestSystemPermissions(
        activity: android.app.Activity,
        request: android.webkit.PermissionRequest,
        cameraNeeded: Boolean,
        micNeeded: Boolean
    ) {
        val permissionsToRequest = mutableListOf<String>()
        if (cameraNeeded) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.CAMERA)
            }
        }
        if (micNeeded) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            val list = mutableListOf<String>()
            if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                list.add(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            }
            if (cameraNeeded) list.add(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            if (micNeeded) list.add(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            request.grant(list.toTypedArray())
        } else {
            // Save pending state for onRequestPermissionsResult processing
            pendingPermissionRequest = request
            pendingPermissionCamera = cameraNeeded
            pendingPermissionMic = micNeeded
            registerPermissionHandler(102) { grantResults ->
                handlePermissionResult(grantResults)
            }
            activity.requestPermissions(permissionsToRequest.toTypedArray(), 102)
            // Do NOT call request.deny() here — the request remains pending
            // and will be granted or denied in handlePermissionResult()
        }
    }

    /**
     * Called by the Activity from onRequestPermissionsResult after a system
     * permission request completes. Grants or denies the pending web permission.
     */
    fun handlePermissionResult(grantResults: IntArray) {
        val request = pendingPermissionRequest ?: return
        val cameraGranted = pendingPermissionCamera &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        val micGranted = pendingPermissionMic &&
            grantResults.size > 1 &&
            grantResults[1] == android.content.pm.PackageManager.PERMISSION_GRANTED

        val list = mutableListOf<String>()
        if (request.resources.contains(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
            list.add(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        }
        if (cameraGranted) list.add(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        if (micGranted) list.add(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)

        if (list.isNotEmpty()) {
            request.grant(list.toTypedArray())
        } else {
            request.deny()
        }

        pendingPermissionRequest = null
        pendingPermissionCamera = false
        pendingPermissionMic = false
    }

    private val TAG_LOC = "YueGeolocation"

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: android.webkit.GeolocationPermissions.Callback?
    ) {
        android.util.Log.d(TAG_LOC, "onGeolocationPermissionsShowPrompt origin=$origin callback=${callback != null}")

        if (callback == null || origin == null) {
            android.util.Log.w(TAG_LOC, "BLOCK: origin=$origin callback=$callback (null guard)")
            return
        }

        val context = context
        val activity = findActivity(context) ?: com.yue.browser.MainActivity.getActiveActivity()
        if (activity == null) {
            android.util.Log.e(TAG_LOC, "BLOCK: activity is null — origin=$origin")
            callback.invoke(origin, false, false)
            return
        }

        val host = try { android.net.Uri.parse(origin).host ?: origin } catch (e: Exception) { origin }
        val cleanHost = host.removePrefix("www.").removePrefix("m.").lowercase()

        val settings = settingsRepository.settingsFlow.value
        val siteMap = settings.sitePermissions[cleanHost] ?: emptyMap()
        val locationGranted = siteMap["location"]
        android.util.Log.d(TAG_LOC, "site permission $cleanHost locationGranted=$locationGranted")

        if (locationGranted == false) {
            android.util.Log.i(TAG_LOC, "BLOCK by site rule: $cleanHost (previously blocked)")
            callback.invoke(origin, false, false)
            return
        }

        if (locationGranted == null) {
            android.util.Log.d(TAG_LOC, "No site rule for $cleanHost — showing user dialog")
            mainHandler.post {
                android.app.AlertDialog.Builder(activity)
                    .setTitle("Site Permission Request")
                    .setMessage("$cleanHost wants to access your Location.")
                    .setPositiveButton("Allow") { _, _ ->
                        android.util.Log.i(TAG_LOC, "User ALLOW $cleanHost → requesting Android location permission")
                        settingsRepository.setSitePermission(cleanHost, "location", true)
                        checkAndRequestLocationSystemPermission(activity, origin, callback)
                    }
                    .setNegativeButton("Block") { _, _ ->
                        android.util.Log.i(TAG_LOC, "User BLOCK $cleanHost")
                        settingsRepository.setSitePermission(cleanHost, "location", false)
                        callback.invoke(origin, false, false)
                    }
                    .setNeutralButton("Cancel") { _, _ ->
                        android.util.Log.i(TAG_LOC, "User CANCEL $cleanHost")
                        callback.invoke(origin, false, false)
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            android.util.Log.d(TAG_LOC, "Site already allowed $cleanHost → checking Android permission")
            checkAndRequestLocationSystemPermission(activity, origin, callback)
        }
    }

    override fun onGeolocationPermissionsHidePrompt() {
        android.util.Log.d(TAG_LOC, "onGeolocationPermissionsHidePrompt")
        super.onGeolocationPermissionsHidePrompt()
    }

    private fun checkAndRequestLocationSystemPermission(
        activity: android.app.Activity,
        origin: String,
        callback: android.webkit.GeolocationPermissions.Callback
    ) {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        android.util.Log.d(TAG_LOC, "Android permission check: fine=$hasFine coarse=$hasCoarse for origin=$origin")

        if (hasFine || hasCoarse) {
            android.util.Log.i(TAG_LOC, "ALLOW: Android permission already granted → callback.invoke(true)")
            callback.invoke(origin, true, true)
        } else {
            android.util.Log.i(TAG_LOC, "Requesting Android FINE+COARSE location permission (requestCode=103)")
            // Save pending state for after system permission result
            pendingLocationCallback = callback
            pendingLocationOrigin = origin
            registerPermissionHandler(103) { grantResults ->
                handleLocationResult(grantResults)
            }
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                103
            )
            // Do NOT call callback.invoke(false) here — it will be handled in handleLocationResult()
        }
    }

    /**
     * Called by the Activity from onRequestPermissionsResult after a location
     * permission request completes.
     */
    fun handleLocationResult(grantResults: IntArray) {
        android.util.Log.d(TAG_LOC, "handleLocationResult grantResults=${grantResults.toList()}")
        val callback = pendingLocationCallback
        val origin = pendingLocationOrigin

        if (callback == null) {
            android.util.Log.e(TAG_LOC, "handleLocationResult but pendingLocationCallback is NULL — lost state!")
            return
        }

        val fineGranted = grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = grantResults.size > 1 &&
            grantResults[1] == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            android.util.Log.i(TAG_LOC, "ALLOW from system: fine=$fineGranted coarse=$coarseGranted → callback.invoke(true)")
            callback.invoke(origin, true, true)
        } else {
            android.util.Log.i(TAG_LOC, "BLOCK from system: fine=$fineGranted coarse=$coarseGranted → callback.invoke(false)")
            callback.invoke(origin, false, false)
        }

        pendingLocationCallback = null
        pendingLocationOrigin = ""
    }

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        android.util.Log.d("YueConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (at ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
        return super.onConsoleMessage(consoleMessage)
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        when (message) {
            "__YuePicker__" -> {
                session.handleElementPickerSubmit(defaultValue ?: "")
                result?.confirm("")
                return true
            }
            "__YuePickerCancel__" -> {
                session.handleElementPickerCancel()
                result?.confirm("")
                return true
            }
        }
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>?>?,
                fileChooserParams: android.webkit.WebChromeClient.FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false

                // Cancel any previously pending chooser (shouldn't happen, but safety)
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = null

                pendingFileChooser = filePathCallback
                val activity = findActivity(context) ?: com.yue.browser.MainActivity.getActiveActivity()
                if (activity == null) {
                    filePathCallback.onReceiveValue(null)
                    pendingFileChooser = null
                    return false
                }

                val isMultiple = try {
                    fileChooserParams?.mode?.and(android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) != 0
                } catch (_: Exception) { false }

                val intent = if (fileChooserParams?.mode?.and(android.webkit.WebChromeClient.FileChooserParams.MODE_SAVE) != 0) {
                    // Save mode — ACTION_CREATE_DOCUMENT
                    android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                } else {
                    // Open mode — ACTION_GET_CONTENT
                    android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, isMultiple)
                    }
                }

                pendingFileChooserAcceptMultiple = isMultiple

                val chooserIntent = android.content.Intent.createChooser(intent, "Pilih file")
                try {
                    activity.startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE)
                    return true
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebChromeClient", "Failed to launch file chooser", e)
                    filePathCallback.onReceiveValue(null)
                    pendingFileChooser = null
                    return false
                }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val newUrl = view?.url ?: ""
                if ((newUrl.isBlank() || newUrl == "about:blank") && !session.isDeliberateNewTab) {
                    return
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.progress = newProgress
                if (view != null) session.updateNavigationState(view)
                
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.combinedCanGoBack,
                    session.combinedCanGoForward
                )

                val currentSettings = settingsRepository.settingsFlow.value
                val isAdBlockActive = currentSettings.isAdBlockEnabled || currentSettings.enabledAddons.contains("ublock")
                if (newProgress > 40 && isAdBlockActive) {
                    AdBlockEngine.injectCosmeticFilters(context, view, view?.url, currentSettings)
                }
            }

            override fun onReceivedTitle(view: WebView?, t: String?) {
                val newUrl = view?.url ?: ""
                if ((newUrl.isBlank() || newUrl == "about:blank") && !session.isDeliberateNewTab) {
                    return
                }
                session.title = if (newUrl == "yue://newtab" || newUrl.isBlank() || newUrl == "about:blank") {
                    "New Tab"
                } else {
                    t ?: "Yue Browser"
                }
                val normalizedUrl = if (newUrl == "about:blank") "yue://newtab" else newUrl
                session.stateCallback?.invoke(
                    normalizedUrl,
                    session.title,
                    session.progress,
                    session.combinedCanGoBack,
                    session.combinedCanGoForward
                )
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (icon != null) {
                    session.faviconCallback?.invoke(icon)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                val activity = findActivity(context) ?: return
                if (activity.isFinishing || activity.isDestroyed) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                
                customView = view
                customViewCallback = callback
                
                session.view.visibility = View.GONE
                
                // Create custom container wrapping customView, the lock button, and speedup badge
                val container = FullscreenContainer(context)
                fullscreenContainer = container
                
                container.addView(customView, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
                
                // Create lock button (smaller size 48dp, padding 15dp)
                val density = context.resources.displayMetrics.density
                val buttonSize = (48 * density).toInt()
                val padding = (15 * density).toInt()
                
                val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#40000000")) // 25% alpha black
                    setStroke(
                        (1f * density).toInt(),
                        android.graphics.Color.parseColor("#4DFFFFFF") // Subtle 30% alpha white line
                    )
                }
                
                val isLockedPref = settingsRepository.settingsFlow.value.isVideoOrientationLocked
                val initialIcon = if (isLockedPref) {
                    com.yue.browser.R.drawable.ic_orientation_lock
                } else {
                    com.yue.browser.R.drawable.ic_orientation_unlock
                }
                val btn = android.widget.ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                        leftMargin = (16 * density).toInt()
                    }
                    background = bgDrawable
                    setImageResource(initialIcon)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    setPadding(padding, padding, padding, padding)
                    elevation = 8 * density
                    alpha = 0f
                    visibility = android.view.View.GONE
                }
                
                btn.setOnClickListener {
                    isOrientationLocked = !isOrientationLocked
                    settingsRepository.setVideoOrientationLocked(isOrientationLocked)
                    val act = findActivity(context)
                    if (act != null) {
                        if (isOrientationLocked) {
                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            btn.setImageResource(com.yue.browser.R.drawable.ic_orientation_lock)
                        } else {
                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            btn.setImageResource(com.yue.browser.R.drawable.ic_orientation_unlock)
                        }
                    }
                    
                    // Vibrate haptic feedback
                    try {
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(40)
                        }
                    } catch (_: Exception) {}
                    
                    // Reschedule auto-hide
                    hideRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        hideLockButton(btn)
                    }
                    hideRunnable = runnable
                    mainHandler.postDelayed(runnable, 2000)
                }
                
                // Create speedup badge
                val badge = android.widget.TextView(context).apply {
                    val badgeBg = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(android.graphics.Color.parseColor("#4D000000")) // 30% alpha black, matching speedup badge theme
                        cornerRadius = 16 * density
                    }
                    background = badgeBg
                    
                    // Padding: 5dp top/bottom, 12dp left/right
                    val padTopBottom = (5 * density).toInt()
                    val padLeftRight = (12 * density).toInt()
                    setPadding(padLeftRight, padTopBottom, padLeftRight, padTopBottom)
                    
                    setTextColor(android.graphics.Color.parseColor("#E6FFFFFF")) // 90% alpha white
                    textSize = 11f // 11sp
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    
                    val lp = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                        topMargin = (16 * density).toInt()
                    }
                    layoutParams = lp
                    
                    visibility = android.view.View.GONE
                    alpha = 0f
                }
                
                container.addView(btn)
                container.addView(badge)
                container.lockButton = btn
                this.lockButton = btn
                this.speedupBadge = badge
                isOrientationLocked = isLockedPref
                
                container.onTouchScreen = {
                    val lBtn = this.lockButton
                    if (lBtn != null) {
                        if (lBtn.visibility == android.view.View.VISIBLE && lBtn.alpha > 0.5f) {
                            hideLockButton(lBtn)
                        } else {
                            showLockButton(lBtn)
                        }
                    }
                }
                
                container.onSpeedupStart = {
                    val settings = settingsRepository.settingsFlow.value
                    if (settings.isVideoSpeedupEnabled) {
                        // 1. Show native speedup badge
                        showSpeedupBadge(settings.videoSpeedupRate)
                        // 2. Evaluate Javascript to speed up video
                        val formattedRate = String.format(java.util.Locale.US, "%.2f", settings.videoSpeedupRate).trimEnd('0').trimEnd('.')
                        val js = """
                            (function() {
                                window.__yue_is_speeding_up__ = true;
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    var v = videos[i];
                                    if (v.__yue_original_rate__ === undefined) {
                                        v.__yue_original_rate__ = v.playbackRate || 1.0;
                                    }
                                    if (!v.paused && !v.ended) {
                                        var targetRate = $formattedRate;
                                        var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                        try { setter.call(v, targetRate); } catch(e) { v.playbackRate = targetRate; }
                                    }
                                }
                            })();
                        """.trimIndent()
                        (session.view as? android.webkit.WebView)?.evaluateJavascript(js, null)
                        
                        // Vibrate brief haptic feedback on hold start
                        try {
                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(40)
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                container.onSpeedupEnd = {
                    val settings = settingsRepository.settingsFlow.value
                    if (settings.isVideoSpeedupEnabled) {
                        // 1. Hide native speedup badge
                        hideSpeedupBadge()
                        // 2. Evaluate Javascript to restore original speed
                        val formattedRate = String.format(java.util.Locale.US, "%.2f", settings.videoSpeedupRate).trimEnd('0').trimEnd('.')
                        val js = """
                            (function() {
                                window.__yue_is_speeding_up__ = false;
                                var videos = document.querySelectorAll('video');
                                for (var i = 0; i < videos.length; i++) {
                                    var v = videos[i];
                                    if (v.__yue_original_rate__ !== undefined) {
                                        var orig = v.__yue_original_rate__;
                                        delete v.__yue_original_rate__;
                                        var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                        try { setter.call(v, orig); } catch(e) { v.playbackRate = orig; }
                                    } else {
                                        // Fallback: If original rate was not saved (e.g. due to race condition
                                        // or video was paused during hold), reset to 1.0 as safest default
                                        // only if current rate matches the sped-up rate.
                                        var currentRate = v.playbackRate || 1.0;
                                        var targetRate = $formattedRate;
                                        if (parseFloat(currentRate.toFixed(2)) === parseFloat(targetRate)) {
                                            var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                            try { setter.call(v, 1.0); } catch(e) { v.playbackRate = 1.0; }
                                        }
                                    }
                                }
                            })();
                        """.trimIndent()
                        (session.view as? android.webkit.WebView)?.evaluateJavascript(js, null)
                    }
                }
                
                // Signal JS that we're in Android fullscreen (suppress JS badge)
                try {
                    (session.view as? android.webkit.WebView)?.evaluateJavascript("window.__yue_in_fullscreen__ = true;", null)
                } catch (_: Exception) {}

                try {
                    val decorView = activity.window.decorView as? android.view.ViewGroup
                    decorView?.addView(container, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebChromeClient", "Error adding custom view container", e)
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                    fullscreenContainer = null
                    lockButton = null
                    speedupBadge = null
                    session.view.visibility = View.VISIBLE
                    return
                }
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
                
                if (isOrientationLocked) {
                    val config = context.resources.configuration
                    if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    } else {
                        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                } else {
                    activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                }
                
                // Show initially for 2 seconds
                showLockButton(btn)
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                hideRunnable?.let { mainHandler.removeCallbacks(it) }
                hideRunnable = null
                
                val activity = findActivity(context) ?: return
                if (activity.isFinishing || activity.isDestroyed) return
                
                val decorView = activity.window.decorView as? android.view.ViewGroup
                
                val container = fullscreenContainer
                if (container != null) {
                    try {
                        decorView?.removeView(container)
                    } catch (e: Exception) {
                        android.util.Log.e("SystemWebChromeClient", "Error removing custom view container", e)
                    }
                    fullscreenContainer = null
                }
                
                customView = null
                lockButton = null
                speedupBadge = null
                isOrientationLocked = false
                
                // Signal JS that fullscreen ended (re-enable JS badge)
                try {
                    (session.view as? android.webkit.WebView)?.evaluateJavascript("window.__yue_in_fullscreen__ = false;", null)
                } catch (_: Exception) {}

                try {
                    session.view.visibility = View.VISIBLE
                } catch (e: Exception) {
                    android.util.Log.e("SystemWebChromeClient", "Error restoring session view visibility", e)
                }
                
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                // Lock to portrait when exiting fullscreen (no auto-rotate) -> change to unspecified to allow auto rotation
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                android.util.Log.d("SystemWebChromeClient", "onCloseWindow called, closing tab, url=${session.url}")
                val activity = findActivity(context)
                activity?.runOnUiThread {
                    session.requestCloseCallback?.invoke()
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                android.util.Log.d("SystemWebChromeClient", "onCreateWindow isDialog=$isDialog isUserGesture=$isUserGesture url=${view?.url}")
                if (session.elementPickerCallback != null) {
                    android.util.Log.d("SystemWebChromeClient", "Blocked popup window creation because element picker is active")
                    return false
                }
                val settings = settingsRepository.settingsFlow.value
                val isAdBlockActive = AdBlockEngine.isAdblockActive(settings)
                val currentHost = try {
                    val openerUrl = view?.url
                    if (openerUrl.isNullOrBlank() || openerUrl == "about:blank") {
                        android.net.Uri.parse(session.url).host ?: ""
                    } else {
                        android.net.Uri.parse(openerUrl).host ?: ""
                    }
                } catch (e: Exception) {
                    try {
                        android.net.Uri.parse(session.url).host ?: ""
                    } catch (e2: Exception) { ""
                    }
                }
                
                val hitTestResult = view?.hitTestResult

                if (isAdBlockActive) {
                    if (AdBlockEngine.shouldBlockPopup(currentHost, isUserGesture)) {
                        android.util.Log.d("SystemWebChromeClient", "Blocked popup creation: currentHost=$currentHost isUserGesture=$isUserGesture")
                        return false
                    }

                    val destinationUrl = hitTestResult?.extra
                    if (!destinationUrl.isNullOrBlank()) {
                        android.util.Log.d("SystemWebChromeClient", "onCreateWindow destinationUrl=$destinationUrl")
                        // Auto-mark domain popup ke skip list supaya kalau history
                        // parent / popup chain tetap menambah entry, goBack() otomatis skip
                        session.markRedirectUrlForSkip(destinationUrl)
                        if (AdBlockEngine.isUrlRedirectingToBlocked(context, destinationUrl, settings) || AdBlockEngine.isSearchEngineWithJudolQuery(context, destinationUrl)) {
                            android.util.Log.d("SystemWebChromeClient", "Blocked popup: destinationUrl blocked by adblock")
                            return false
                        }
                    }
                }

                val tempWebView = object : WebView(context) {
                    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
                        val connection = super.onCreateInputConnection(outAttrs)
                        if (session.isPrivate) {
                            outAttrs.imeOptions = outAttrs.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                        return connection
                    }
                }
                tempWebView.settings.javaScriptEnabled = true
                tempWebView.settings.domStorageEnabled = true
                tempWebView.settings.databaseEnabled = true
                tempWebView.settings.useWideViewPort = true
                tempWebView.settings.loadWithOverviewMode = true
                tempWebView.settings.javaScriptCanOpenWindowsAutomatically = true
                tempWebView.settings.userAgentString = view?.settings?.userAgentString ?: UserAgentManager.getExpectedUserAgent("", false, settingsRepository.settingsFlow.value)
                
                val isAnchorLink = hitTestResult != null &&
                    (hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                     hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                tempWebView.setTag(987654321, !isAnchorLink)

                // Check if destination URL is a redirect chain — tag the tempWebView and
                // navigate DIRECTLY to the decoded target URL, bypassing ad networks (Brave-style).
                val destUrl = hitTestResult?.extra
                val destIsRedirectChain = destUrl?.let { url ->
                    try {
                        val uri = android.net.Uri.parse(url)
                        uri.queryParameterNames.any { param ->
                            val value = uri.getQueryParameter(param) ?: ""
                            if (value.startsWith("http://") || value.startsWith("https://")) true
                            else if (value.length > 20 && value.length % 4 == 0 && value.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                                try {
                                    val decoded = android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                    String(decoded, Charsets.UTF_8).startsWith("http")
                                } catch (_: Exception) { false }
                            } else false
                        }
                    } catch (_: Exception) { false }
                } ?: false
                if (destIsRedirectChain) {
                    tempWebView.setTag(987654322, true)
                    // Extract and load the decoded target URL directly on the tempWebView
                    // BEFORE sending it via transport. This is safe because the tempWebView
                    // is newly created and not yet attached to any window.
                    val decodedTarget = destUrl?.let { url ->
                        try {
                            val uri = android.net.Uri.parse(url)
                            for (param in uri.queryParameterNames) {
                                val value = uri.getQueryParameter(param) ?: continue
                                if (value.startsWith("http://") || value.startsWith("https://")) {
                                    return@let value
                                }
                                if (value.length > 20 && value.length % 4 == 0 && value.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                                    try {
                                        val decoded = android.util.Base64.decode(value, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                                        val decodedStr = String(decoded, Charsets.UTF_8)
                                        if (decodedStr.startsWith("http")) return@let decodedStr
                                    } catch (_: Exception) { }
                                }
                            }
                            null
                        } catch (_: Exception) { null }
                    }
                    if (decodedTarget != null) {
                        android.util.Log.d("SystemWebChromeClient", "Loading redirect target directly on popup: $decodedTarget")
                        tempWebView.loadUrl(decodedTarget)
                    } else {
                        android.util.Log.d("SystemWebChromeClient", "Tagged tempWebView as redirect chain popup (destination=$destUrl)")
                    }
                }

                session.newTabWithWebViewCallback?.invoke(tempWebView, isPrivate, currentHost)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

private class FullscreenContainer(context: android.content.Context) : android.widget.FrameLayout(context) {
    var lockButton: android.view.View? = null
    var speedupBadge: android.view.View? = null
    var onTouchScreen: (() -> Unit)? = null
    var onSpeedupStart: (() -> Unit)? = null
    var onSpeedupEnd: (() -> Unit)? = null

    private var isHolding = false
    private var hasTriggeredSpeedup = false
    private var holdDetectorRunnable: Runnable? = null
    private var speedupTimeoutRunnable: Runnable? = null
    private val density = context.resources.displayMetrics.density

    /**
     * Attach hold gesture detection directly to the video view.
     * Using OnTouchListener on the target view is more reliable than
     * dispatchTouchEvent because video views may consume touch events
     * and prevent ACTION_UP from reaching the parent container.
     */
    fun setupHoldGesture(targetView: android.view.View) {
        targetView.setOnTouchListener { _, ev ->
            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startHoldDetector(ev.x, ev.y, ev.rawX, ev.rawY)
                    false // let event pass through to video player
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    checkMoveCancel(ev.x, ev.y)
                    false // let event pass through
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    endHoldDetector(ev.rawX, ev.rawY)
                    false // let event pass through
                }
                else -> false
            }
        }
    }

    private fun startHoldDetector(x: Float, y: Float, rawX: Float, rawY: Float) {
        isHolding = false
        hasTriggeredSpeedup = false

        // Check if touch is on lock button
        val btn = lockButton
        var touchOnButton = false
        if (btn != null && btn.visibility == android.view.View.VISIBLE) {
            val rect = android.graphics.Rect()
            btn.getGlobalVisibleRect(rect)
            if (rect.contains(rawX.toInt(), rawY.toInt())) {
                touchOnButton = true
            }
        }

        if (!touchOnButton) {
            // Start hold detector after 500ms
            val runnable = Runnable {
                if (!hasTriggeredSpeedup) {
                    hasTriggeredSpeedup = true
                    isHolding = true
                    onSpeedupStart?.invoke()
                    // Set timeout to auto-cancel speedup after 10 seconds
                    val timeoutRunnable = Runnable {
                        if (isHolding) {
                            onSpeedupEnd?.invoke()
                            isHolding = false
                            hasTriggeredSpeedup = false
                        }
                    }
                    speedupTimeoutRunnable = timeoutRunnable
                    postDelayed(timeoutRunnable, 10000)
                }
            }
            holdDetectorRunnable = runnable
            postDelayed(runnable, 500)
        }
    }

    private fun checkMoveCancel(x: Float, y: Float) {
        // Cancel hold detector if moved too far
        holdDetectorRunnable?.let {
            removeCallbacks(it)
            holdDetectorRunnable = null
        }
        speedupTimeoutRunnable?.let {
            removeCallbacks(it)
            speedupTimeoutRunnable = null
        }
        // If hold was already triggered, cancel speedup immediately
        if (hasTriggeredSpeedup) {
            hasTriggeredSpeedup = false
            if (isHolding) {
                onSpeedupEnd?.invoke()
                isHolding = false
            }
        }
    }

    private fun endHoldDetector(rawX: Float, rawY: Float) {
        holdDetectorRunnable?.let {
            removeCallbacks(it)
            holdDetectorRunnable = null
        }
        speedupTimeoutRunnable?.let {
            removeCallbacks(it)
            speedupTimeoutRunnable = null
        }

        if (hasTriggeredSpeedup || isHolding) {
            onSpeedupEnd?.invoke()
        }
        hasTriggeredSpeedup = false
        isHolding = false

        val badge = speedupBadge
        if (badge != null && badge.visibility == android.view.View.VISIBLE) {
            onSpeedupEnd?.invoke()
        }

        // It was a tap — check lock button
        val btn = lockButton
        var touchOnButton = false
        if (btn != null && btn.visibility == android.view.View.VISIBLE) {
            val rect = android.graphics.Rect()
            btn.getGlobalVisibleRect(rect)
            if (rect.contains(rawX.toInt(), rawY.toInt())) {
                touchOnButton = true
            }
        }
        if (!touchOnButton) {
            onTouchScreen?.invoke()
        }
    }

    // Keep dispatchTouchEvent as a fallback for touches outside the video view
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)
        // Only handle lock button touches here; video hold is handled by OnTouchListener
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val btn = lockButton
                if (btn != null && btn.visibility == android.view.View.VISIBLE) {
                    val rect = android.graphics.Rect()
                    btn.getGlobalVisibleRect(rect)
                    if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        // Touch on lock button — let it pas
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

