package com.yue.browser.data.adblock.service

import android.util.Log
import android.webkit.WebView

/**
 * Service: YouTube-specific ad blocking via JS injection.
 * Extracted from AdBlockEngine for separation of concerns.
 */
class YouTubeService {
    private val tag = "YouTubeService"

    fun isYouTubeUrl(url: String?): Boolean {
        if (url == null) return false
        return try {
            val host = android.net.Uri.parse(url).host ?: ""
            host.contains("youtube.com")
        } catch (e: Exception) {
            false
        }
    }

    fun injectAdBlock(view: WebView?, url: String?) {
        if (!isYouTubeUrl(url)) return
        
        val js = """
(function() {
    try {
    if (window._yue_yt_adblock) return;
    window._yue_yt_adblock = true;
    var _fetch = window.fetch.bind(window);
    window.fetch = function(r, o) {
        try {
        var u = (typeof r === 'string') ? r : (r && r.url ? r.url : '');
        if (u.includes('youtubei/v1/player') || u.includes('youtubei/v1/browse') || u.includes('youtubei/v1/next') || u.includes('googlevideo.com') || u.includes('ytimg.com')) {
            return _fetch(r, o);
        }
        if (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||u.includes('/get_midroll')||u.includes('yt.ads')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('youtube.com/api/stats/ads')||u.includes('youtube.com/pagead/')) {
            return Promise.resolve(new Response('',{status:204}));
        }
        } catch(e) {}
        return _fetch(r, o);
    };
    var _open = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, u) {
        try {
        if (typeof u === 'string') {
            if (u.includes('youtubei/v1/player') || u.includes('youtubei/v1/browse') || u.includes('youtubei/v1/next') || u.includes('googlevideo.com') || u.includes('ytimg.com')) {
                return _open.apply(this, arguments);
            }
            if (u.includes('googleads')||u.includes('doubleclick')||u.includes('pagead2')||u.includes('pagead')||u.includes('adservice')||u.includes('googlesyndication')||u.includes('ad_break')||u.includes('adunit')||u.includes('/get_midroll')||u.includes('yt.ads')||u.includes('ad_type=')||u.includes('ad_preroll')||u.includes('admodule=')||u.includes('masthead=')||u.includes('youtube.com/api/stats/ads')||u.includes('youtube.com/pagead/')) {
                u = '//localhost/blocked?' + Date.now();
            }
        }
        } catch(e) {}
        return _open.apply(this, arguments);
    };
    function hideSponsored() {
        try {
        var elements = document.querySelectorAll('ytm-rich-section-renderer, ytm-rich-item-renderer, ytm-item-section-renderer, ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, ytm-promoted-item-renderer, [class*="promoted"], [class*="sponsored"]');
        elements.forEach(function(el) {
            try {
            if (el.querySelector('.ytm-ad-badge') || el.querySelector('[class*="ad-badge"]') || el.querySelector('[class*="-ad-"]') || el.querySelector('.ytm-ad-label')) {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0', 'important');
                return;
            }
            var text = el.innerText || '';
            if (text.includes('Sponsored') || text.includes('Bersponsor') || text.includes('Iklan') || text.includes('Promoted') || text.includes('Sponsor')) {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0', 'important');
            }
            } catch(e) {}
        });
        } catch(e) {}
    }
    function hideAdUI() {
        try {
        var p = document.querySelector('.html5-video-player')||document.querySelector('.ytd-player')||document.querySelector('.ytm-video-player');
        var isAd = p && (p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting'));
        var v = document.querySelector('video');
        if (isAd) {
            if (v) { v.style.setProperty('opacity','0','important'); v.style.setProperty('pointer-events','none','important'); }
            if (p) { p.style.setProperty('background','transparent','important'); }
            var adE = p.querySelectorAll('.ytp-ad-player-overlay,.ytp-ad-overlay-container,.ytp-ad-text-overlay,.ytp-ad-image-overlay,.ytp-ad-action-interrupt-slot,.ytp-ad-survey-layer,.ytp-ad-progress,.ytp-ad-text,.ytp-ad-badge');
            for (var i=0;i<adE.length;i++){adE[i].style.setProperty('display','none','important');}
        } else {
            if (v) { v.style.setProperty('opacity','1','important'); v.style.removeProperty('pointer-events'); }
            if (p) { p.style.removeProperty('background'); }
        }
        } catch(e) {}
    }
    function checkAndSkipAd() {
        try {
        var v = document.querySelector('video');
        if (!v) return;
        var player = document.querySelector('.html5-video-player') || document.querySelector('.ytd-player') || document.querySelector('.ytm-video-player');
        var isAdVideo = false;
        if (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
            isAdVideo = true;
        }
        var skipBtns = document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad,.ytp-ad-skip-button-container button,.ytm-skip-ad,.ytm-ad-skip-button');
        var hasSkipBtn = false;
        for (var i = 0; i < skipBtns.length; i++) {
            if (skipBtns[i].offsetParent !== null) { 
                hasSkipBtn = true; 
                try { skipBtns[i].click(); } catch(e) {}
            }
        }
        if (isAdVideo || hasSkipBtn) {
            if (v.duration > 0 && v.currentTime < v.duration - 0.5) { 
                try { v.currentTime = v.duration - 0.1; } catch(e) {} 
            }
            if (v.paused) { try { v.play(); } catch(e) {} }
        }
        hideAdUI();
        } catch(e) {}
    }
    try {
    var obs = new MutationObserver(function(m) {
        try {
        for (var i = 0; i < m.length; i++) {
            for (var j = 0; j < m[i].addedNodes.length; j++) {
                var n = m[i].addedNodes[j];
                if (n.tagName === 'SCRIPT' && n.src && (n.src.includes('doubleclick')||n.src.includes('pagead')||n.src.includes('googleads')||n.src.includes('googlesyndication'))) {
                    try { n.remove(); } catch(e) {}
                }
            }
        }
        hideSponsored();
        checkAndSkipAd();
        } catch(e) {}
    });
    if (document.documentElement) obs.observe(document.documentElement, {childList:true, subtree:true});
    } catch(e) {}
    try {
    var s = document.createElement('style');
    s.id = 'yue-yt-adblock';
    s.textContent = 'ytd-video-masthead-ad-v3-renderer,ytd-ad-slot-renderer,ytd-action-companion-ad-renderer,ytd-promoted-video-renderer,ytd-in-feed-ad-layout-renderer,ytd-display-ad-renderer,ytd-banner-promo-renderer,ytd-video-ad,.video-ads,.ytp-ad-module,#masthead-ad,.ytp-ad-image-overlay,.ytp-ad-text-overlay,.ytd-companion-ad-renderer,.ytd-search-pyv-renderer,.ytp-ad-survey-layer,.ytp-ad-action-interrupt-slot,.ytm-masthead-ad,.ytm-ad-badge,.ytm-promoted-video,.ytm-display-ad,.ytm-companion-ad,.ytm-ad-slot,.ytm-video-ad,.ytm-promoted-video-container,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-item-renderer,ytm-carousel-promoted-item-renderer,ytm-brand-video-singleton-renderer,ytm-brand-video-shelf-renderer,ytm-in-feed-ad-layout-renderer,ytm-ad-layout-renderer,ytm-sponsored-card,ytm-promoted-product-renderer,.ytp-ad-player-overlay,.ytp-ad-overlay-container,.ytp-ad-progress,.ytp-ad-text,#player-ads,.ytp-ad-notification,.ytp-ad-visit-website-button,.ytp-ad-badge,.ytp-ad-button,ytm-rich-item-renderer:has(.ytm-ad-badge),ytm-rich-section-renderer:has(.ytm-ad-badge),ytm-item-section-renderer:has(.ytm-ad-badge),ytm-rich-item-renderer:has([class*="ad-badge"]),ytm-rich-section-renderer:has([class*="ad-badge"]),ytm-item-section-renderer:has([class*="ad-badge"]),ytm-rich-item-renderer:has(.ytm-ad-label),ytm-rich-section-renderer:has(.ytm-ad-label),ytm-item-section-renderer:has(.ytm-ad-label){display:none!important;height:0!important;min-height:0!important;opacity:0!important;pointer-events:none!important;z-index:-1!important;position:absolute!important;top:-9999px!important}';
    if (document.documentElement) document.documentElement.appendChild(s);
    } catch(e) {}
    var patchConfig = function() {
        try {
        if (window.yt && window.yt.config_ && window.yt.config_.INNERTUBE_CONTEXT && window.yt.config_.INNERTUBE_CONTEXT.client) {
            var c = window.yt.config_.INNERTUBE_CONTEXT.client;
            c.adSignals = undefined; try { delete c.adSignals; } catch(e) {}
        }
        if (window.yt && window.yt.config_) {
            try { window.yt.config_.adAcknowledge = undefined; delete window.yt.config_.adAcknowledge; } catch(e) {}
            try { window.yt.config_.adManager = undefined; delete window.yt.config_.adManager; } catch(e) {}
            try { window.yt.config_.adsense = undefined; delete window.yt.config_.adsense; } catch(e) {}
            try { window.yt.config_.pageid = undefined; delete window.yt.config_.pageid; } catch(e) {}
        }
        if (window.ytcfg) {
            try { window.ytcfg.set('ADS_ALLOWED', false); } catch(e) {}
        }
        } catch(e) {}
    };
    patchConfig();
    setInterval(patchConfig, 1000);
    
    // Event-driven video ad skipping
    document.addEventListener('play', checkAndSkipAd, true);
    document.addEventListener('playing', checkAndSkipAd, true);
    document.addEventListener('timeupdate', checkAndSkipAd, true);
    
    setInterval(function() {
        checkAndSkipAd();
        hideSponsored();
    }, 100);
    } catch(e) {}
})();
        """.trimIndent()
        view?.post {
            try {
                view.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.e(tag, "Error evaluating YouTube adblock", e)
            }
        }
    }
}