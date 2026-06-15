package com.yue.browser.data.engine

import android.content.Context
import android.webkit.JavascriptInterface

class SystemWebViewMediaSessionInterface(
    private val context: Context,
    private val session: SystemWebViewSession
) {
    @JavascriptInterface
    fun updateMetadata(title: String, artist: String, album: String, artworkUrl: String) {
        if (session.isDestroyed) return
        MediaSessionManager.updateMetadata(context, session, title, artist, album, artworkUrl)
    }

    @JavascriptInterface
    fun updatePlaybackState(isPlaying: Boolean) {
        if (session.isDestroyed) return
        MediaSessionManager.updatePlaybackState(context, session, isPlaying)
    }
}
