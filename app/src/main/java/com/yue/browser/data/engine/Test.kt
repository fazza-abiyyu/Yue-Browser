package com.yue.browser.data.engine
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
fun test(session: GeckoSession, ext: WebExtension) {
    session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
    }, "yue")
}
