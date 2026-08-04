package com.yue.browser.data.engine

import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SystemWebViewRenderer(
    webViewInstance: WebView,
    progress: Int,
    isGone: Boolean,
    modifier: Modifier,
    onScrollChanged: (visible: Boolean) -> Unit,
    onReload: () -> Unit,
    onTouch: () -> Unit
) {
    val currentOnScrollChanged by rememberUpdatedState(onScrollChanged)
    val currentOnTouch by rememberUpdatedState(onTouch)
    val bgColor = MaterialTheme.colorScheme.background.toArgb()
    AndroidView(
        factory = { ctx ->
            androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                val wv = webViewInstance
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)

                var hasScrolledDownFromTop = false
                var currentScrollY = 0

                wv.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            currentOnTouch()
                            val isAtVeryTop = !wv.canScrollVertically(-1) && wv.scrollY <= 0
                            hasScrolledDownFromTop = !isAtVeryTop
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Dynamically update: if the WebView can scroll up or
                            // scrollY > 0, the user has moved from the top.
                            if (wv.canScrollVertically(-1) || wv.scrollY > 0) {
                                hasScrolledDownFromTop = true
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Keep hasScrolledDownFromTop as-is for this gesture,
                            // but it will be reset on the next ACTION_DOWN.
                        }
                    }
                    false
                }

                wv.removeJavascriptInterface("YueScroll")
                wv.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onScrollChanged(visible: Boolean) {
                        wv.post {
                            currentOnScrollChanged(visible)
                        }
                    }
                }, "YueScroll")

                var isNavVisible = true
                val scrollThreshold = 15
                wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    currentScrollY = scrollY
                    val diff = scrollY - oldScrollY
                    if (scrollY > 2) {
                        hasScrolledDownFromTop = true
                    }
                    if (scrollY <= 2) {
                        if (!isNavVisible) {
                            isNavVisible = true
                            currentOnScrollChanged(true)
                        }
                    } else if (Math.abs(diff) > scrollThreshold) {
                        if (diff > 0 && scrollY > 50) {
                            if (isNavVisible) {
                                isNavVisible = false
                                currentOnScrollChanged(false)
                            }
                        } else if (diff < 0) {
                            if (!isNavVisible) {
                                isNavVisible = true
                                currentOnScrollChanged(true)
                            }
                        }
                    }
                }

                addView(wv, android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                ))
                setOnRefreshListener {
                    onReload()
                }
                setOnChildScrollUpCallback { _, _ ->
                    // Prevent refresh if user has scrolled down from top.
                    // For manga/comic sites that use nested scroll containers,
                    // we also check the current scroll position dynamically.
                    if (hasScrolledDownFromTop || currentScrollY > 2) {
                        true // Child can scroll, don't intercept
                    } else {
                        wv.canScrollVertically(-1)
                    }
                }
                setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
                setSlingshotDistance((80 * ctx.resources.displayMetrics.density).toInt())
                setProgressViewOffset(false, 0, (40 * ctx.resources.displayMetrics.density).toInt())
                isEnabled = true
            }
        },
        update = { swipeRefreshLayout ->
            // Match container background to theme to prevent white flash during loading
            swipeRefreshLayout.setBackgroundColor(bgColor)

            if (progress >= 100 && swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }

            val targetVisibility = if (isGone) View.GONE else View.VISIBLE
            if (swipeRefreshLayout.visibility != targetVisibility) {
                swipeRefreshLayout.visibility = targetVisibility
            }
        },
        modifier = modifier.graphicsLayer {
            clip = true
        }
    )
}
