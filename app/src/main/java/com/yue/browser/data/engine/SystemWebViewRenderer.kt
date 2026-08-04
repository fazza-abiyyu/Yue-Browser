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
            val swipeLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx)
            val wv = webViewInstance
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)

            // Track simple touch callback invocation
            wv.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    currentOnTouch()
                }
                false
            }

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

            swipeLayout.addView(wv, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
            swipeLayout.setOnRefreshListener {
                onReload()
            }

            swipeLayout.setOnChildScrollUpCallback { _, _ ->
                wv.canScrollVertically(-1) || wv.scrollY > 0
            }

            val displayMetrics = ctx.resources.displayMetrics
            val triggerDistancePx = (120 * displayMetrics.density).toInt()
            val slingshotDistancePx = (150 * displayMetrics.density).toInt()

            swipeLayout.setDistanceToTriggerSync(triggerDistancePx)
            swipeLayout.setSlingshotDistance(slingshotDistancePx)
            swipeLayout.setProgressViewOffset(false, 0, (40 * displayMetrics.density).toInt())
            swipeLayout.isEnabled = true
            swipeLayout
        },
        update = { swipeRefreshLayout ->
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
