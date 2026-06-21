package com.yue.browser.data.engine

import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SystemWebViewRenderer(
    webViewInstance: WebView,
    progress: Int,
    isGone: Boolean,
    modifier: Modifier,
    onScrollChanged: (visible: Boolean) -> Unit,
    onReload: () -> Unit
) {
    val currentOnScrollChanged by rememberUpdatedState(onScrollChanged)
    AndroidView(
        factory = { ctx ->
            androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx).apply {
                val wv = webViewInstance
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)

                var startedAtTop = false
                wv.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val isInTopOneThird = event.y < wv.height / 3f
                            startedAtTop = isInTopOneThird && !wv.canScrollVertically(-1)
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
                    val diff = scrollY - oldScrollY
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
                    !startedAtTop || wv.canScrollVertically(-1)
                }
                setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
                setSlingshotDistance((80 * ctx.resources.displayMetrics.density).toInt())
                setProgressViewOffset(false, 0, (40 * ctx.resources.displayMetrics.density).toInt())
                isEnabled = true
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
        },
        modifier = modifier.graphicsLayer {
            clip = true
        }
    )
}
