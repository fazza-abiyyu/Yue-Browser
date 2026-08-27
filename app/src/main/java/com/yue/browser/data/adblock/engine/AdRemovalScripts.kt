package com.yue.browser.data.adblock.engine

import org.json.JSONObject

object AdRemovalScripts {

    val adClassFragments = arrayOf(
        "ad-", "ad_", "ads-", "ads_", "advert", "advertise", "advertisement",
        "promo", "promotion", "sponsored", "recommend", "recommendation",
        "popup-ad", "popupAd", "popunder", "popin", "popout", "pop-up",
        "overlay-ad", "overlayAd", "floating-ad", "floatingAd",
        "sticky-ad", "stickyAd", "banner-ad", "bannerAd",
        "top-ad", "topAd", "bottom-ad", "bottomAd",
        "side-ad", "sideAd", "sidebar-ad",
        "interstitial", "lightbox-ad", "modal-ad", "modal-overlay",
        "exit-intent", "entry-ad",
        "ad-frame", "adFrame", "ad_frame",
        "google-ad", "googleAd", "doubleclick",
        "adsense", "adserver", "adtech",
        "taboola", "outbrain", "mgid", "contentad",
        "revcontent", "zemanta",
        "ad-slot", "adSlot", "ad-slot",
        "ad-container", "adContainer",
        "ad-wrapper", "adWrapper", "ads-wrapper",
        "promoted-content", "sponsored-content",
        "betting", "casino", "gambling", "slot-",
        "video-ad", "videoAd", "pre-roll", "preroll",
        "div-gpt-ad", "adsbygoogle",
        "cookie-banner", "gdpr-banner", "consent-banner",
        "cookie-notice", "CookieNotice", "CookieBanner",
        "onetrust", "cookiebot", "optanon",
        "trustarc", "truste", "osano", "klaro",
        "ads-overlay", "ad-overlay", "overlay", "floating-bar", "sticky-footer",
        "fixed-footer", "ad-footer", "footer-ad",
        "bottom-banner", "top-banner", "notification-bar",
        "paywall-overlay", "paywall", "subscription-wall",
        "close-ad", "close-banner", "accept-cookies", "cookie-consent",
        "dark-overlay", "overlay-dark", "modal-backdrop",
    )

    val adAttrFragments = arrayOf(
        "data-ad", "data-ads", "data-advertisement", "data-advertiser",
        "data-creative", "data-sponsored", "data-promoted",
        "data-google-query-id", "data-google-ad-client",
        "data-gpt-slot", "data-gpt-ad-unit-path",
        "data-ad-slot", "data-ad-client", "data-ad-format",
        "data-ad-layout", "data-ad-platform", "data-ad-network",
        "data-ad-provider", "data-ad-server", "data-ad-type",
        "data-ad-zone", "data-ad-position",
        "data-nad-slot", "data-nad-client",
        "ad-slot", "ad-sizes",
    )

    fun buildCssInjectionScript(css: String): String {
        var cssToQuote = css
        if (cssToQuote.length > 60000) {
            val lastBrace = cssToQuote.lastIndexOf('}', 60000)
            cssToQuote = if (lastBrace != -1) {
                cssToQuote.substring(0, lastBrace + 1)
            } else {
                cssToQuote.take(60000)
            }
        }
        val quotedCss = try {
            JSONObject.quote(cssToQuote)
        } catch (e: Exception) {
            "\"\""
        }

        return """
        (function() {
            try {
                var css = $quotedCss;
                function injectStyle() {
                    try {
                        if (document.head) {
                            var style = document.getElementById('yue-adblock-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'yue-adblock-style';
                                style.textContent = css;
                                document.head.appendChild(style);
                            } else if (style.textContent !== css) {
                                style.textContent = css;
                            }
                        }
                    } catch(e) {}
                }
                injectStyle();
                if (!window.yueCssObserver) {
                    window.yueCssObserver = new MutationObserver(function(m) {
                        try { injectStyle(); } catch(e) {}
                    });
                    if (document.documentElement) {
                        window.yueCssObserver.observe(document.documentElement, {
                            childList: true, subtree: true
                        });
                    }
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun buildAggressiveAdRemovalScript(): String {
        val classFragments = adClassFragments.joinToString(", ") { "'$it'" }
        val attrFragments = adAttrFragments.joinToString(", ") { "'$it'" }

        return """
        (function() {
            try {
                if (window.__yueAdblockActive) return;
                window.__yueAdblockActive = true;

                var classFragments = [$classFragments];
                var attrFragments = [$attrFragments];

                function hasAdClassName(el) {
                    if (!el.className) return false;
                    var cn = (typeof el.className === 'string') ? el.className : '';
                    var lower = cn.toLowerCase();
                    for (var i = 0; i < classFragments.length; i++) {
                        if (lower.indexOf(classFragments[i]) !== -1) return true;
                    }
                    return false;
                }

                function hasAdAttribute(el) {
                    for (var i = 0; i < el.attributes.length; i++) {
                        var attr = el.attributes[i];
                        var name = attr.name.toLowerCase();
                        var value = (attr.value || '').toLowerCase();
                        for (var j = 0; j < attrFragments.length; j++) {
                            if (name.indexOf(attrFragments[j]) !== -1) return true;
                            if (value.indexOf(attrFragments[j]) !== -1) return true;
                        }
                    }
                    return false;
                }

                function hasAdUrl(el) {
                    var src = (el.src || '').toLowerCase();
                    var href = (el.href || '').toLowerCase();
                    var action = (el.action || '').toLowerCase();
                    var dataSrc = (el.getAttribute('data-src') || '').toLowerCase();
                    var all = src + ' ' + href + ' ' + action + ' ' + dataSrc;
                    if (!all.trim()) return false;
                    var patterns = ['doubleclick', 'googlesyndication', 'googleadservices',
                        'google-analytics', 'googletagmanager', 'googletagservices',
                        'googlesynergy', 'adservice.google', 'adnxs.com', 'openx.net',
                        'rubiconproject', 'adform.net', 'smartadserver',
                        'taboola', 'outbrain', 'mgid.com', 'exoclick',
                        'propellerads', 'popads', 'popcash', 'adsterra',
                        'juicyads', 'masonerthor', 'onclickads',
                        'teads.tv', 'aniview.com', 'connatix', 'primis.tech',
                        'spotxchange', 'tremorvideo', 'brid.tv',
                        'revcontent', 'adblade', 'content.ad', 'zedo.com',
                        'hotjar.com', 'mouseflow.com', 'luckyorange.com',
                        'mixpanel.com', 'segment.io', 'amplitude.com',
                        'chartbeat.com', 'scorecardresearch', 'quantserve.com',
                        'statcounter.com', 'histats.com', 'chartboost.com',
                        'applovin.com', 'ironsrc.com', 'vungle.com',
                        'adcolony.com', 'infolinks.com', 'buysellads.com',
                        'media.net', 'gumgum.com', 'undertone.com', 'sovrn.com',
                        'lijit.com', 'zemanta.com', 'zemantahub.com',
                        'casino', 'gambling', 'betting', 'slot-machine',
                        'tpc.googlesyndication', 'pagead2.googlesyndication',
                        'partner.googleadservices', 'csi.gstatic',
                        'fundingchoicesmessages.google.com'];
                    for (var i = 0; i < patterns.length; i++) {
                        if (all.indexOf(patterns[i]) !== -1) return true;
                    }
                    return false;
                }

                function hasAdText(el) {
                    var text = (el.textContent || el.innerText || '').toLowerCase().trim();
                    if (!text || text.length < 5) return false;
                    var adTexts = ['advertisement', 'sponsored', 'advertisement', 'ad choice',
                        'ad by', 'powered by', 'brought to you by', 'supported by',
                        'iklankan', 'sponsor', 'iklan', 'promosi', 'promosi',
                        'skip ad', 'skip in', 'ad will close', 'ad ends',
                        'close ad', 'close this ad', 'dismiss ad',
                        'ad blocker', 'please disable', 'disable adblock'];
                    for (var i = 0; i < adTexts.length; i++) {
                        if (text.indexOf(adTexts[i]) !== -1) return true;
                    }
                    return false;
                }

                function isLikelyAd(el) {
                    if (!el || !el.parentElement) return false;
                    if (el.nodeType !== 1) return false;
                    var tag = el.tagName ? el.tagName.toLowerCase() : '';
                    if (tag === 'html' || tag === 'body' || tag === 'head' || tag === 'title' || tag === 'meta' || tag === 'script' || tag === 'style' || tag === 'link') return false;

                    var rect;
                    try { rect = el.getBoundingClientRect(); } catch(e) { return false; }
                    if (rect.width === 0 || rect.height === 0) return false;

                    var vw = window.innerWidth;
                    var vh = window.innerHeight;
                    var style = window.getComputedStyle(el);
                    var pos = style.position;
                    var zIdx = parseInt(style.zIndex) || 0;
                    var bgColor = style.backgroundColor;
                    var opacity = parseFloat(style.opacity);

                    if ((pos === 'fixed' || pos === 'absolute') && zIdx > 500) {
                        if (rect.width > vw * 0.4 && rect.height > vh * 0.4) {
                            if (el.children && el.children.length <= 20) return true;
                            if (hasAdClassName(el) || hasAdAttribute(el)) return true;
                        }
                    }

                    if ((pos === 'fixed' || pos === 'absolute') && zIdx > 500) {
                        if (rect.width >= vw && rect.height >= vh) return true;
                        if (rect.width > vw * 0.8 && rect.height > vh * 0.8) return true;
                    }

                    if (pos === 'fixed') {
                        if (rect.width < 500 && rect.height < 500) {
                            var right = vw - (rect.left + rect.width);
                            var bottom = vh - (rect.top + rect.height);
                            var isCorner = (rect.left < 10) || (rect.top < 10) || (right < 10) || (bottom < 10);
                            if (isCorner && tag !== 'nav' && tag !== 'header' && tag !== 'footer') return true;
                        }
                    }

                    if (pos === 'fixed' || pos === 'sticky') {
                        var bottom = vh - (rect.top + rect.height);
                        if (bottom < 5 && rect.width > vw * 0.5 && rect.height < 200) {
                            if (tag !== 'nav' && tag !== 'header') return true;
                        }
                    }

                    if (hasAdClassName(el)) {
                        var id = (el.id || '').toLowerCase();
                        if (id === 'content' || id === 'main' || id === 'article' || id === 'wrapper') return false;
                        return true;
                    }

                    if (hasAdAttribute(el)) return true;

                    if (hasAdUrl(el)) {
                        var parentClass = (el.className || '').toLowerCase();
                        var parentId = (el.id || '').toLowerCase();
                        if (parentClass.indexOf('content') !== -1 || parentId.indexOf('content') !== -1) return false;
                        return true;
                    }

                    if (rect.width < 600 && rect.height < 400) {
                        if (hasAdText(el)) return true;
                    }

                    if ((pos === 'fixed' || pos === 'absolute') && zIdx > 90) {
                        if (opacity < 0.1 || (bgColor.indexOf('rgba(0,0,0,0') !== -1) || bgColor === 'transparent') {
                            if (rect.width > 0 && rect.height > 0) return true;
                        }
                    }

                    return false;
                }

                function removeAd(el) {
                    try {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('opacity', '0', 'important');
                        el.style.setProperty('pointer-events', 'none', 'important');
                        el.style.setProperty('overflow', 'hidden', 'important');
                        el.style.setProperty('height', '0', 'important');
                        el.style.setProperty('max-height', '0', 'important');
                        el.style.setProperty('min-height', '0', 'important');
                        el.style.setProperty('width', '0', 'important');
                        el.style.setProperty('max-width', '0', 'important');
                        el.style.setProperty('min-width', '0', 'important');
                        el.style.setProperty('position', 'absolute', 'important');
                        el.style.setProperty('top', '-99999px', 'important');
                        el.style.setProperty('left', '-99999px', 'important');
                        el.style.setProperty('right', 'auto', 'important');
                        el.style.setProperty('transform', 'scale(0)', 'important');
                        el.style.setProperty('z-index', '-999999', 'important');
                        el.style.setProperty('clip', 'rect(0,0,0,0)', 'important');
                        el.style.setProperty('margin', '0', 'important');
                        el.style.setProperty('padding', '0', 'important');
                        // Also hide any child iframes/videos within ad containers
                        var children = el.querySelectorAll('iframe, video, canvas');
                        for (var ci = 0; ci < children.length; ci++) {
                            var child = children[ci];
                            try {
                                child.style.setProperty('display', 'none', 'important');
                                child.style.setProperty('visibility', 'hidden', 'important');
                                child.style.setProperty('pointer-events', 'none', 'important');
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                function forceEnableSkip() {
                    try {
                        var all = document.querySelectorAll('button, a, input, [role="button"], [class*="skip"], [class*="close"], [id*="skip"], [id*="close"], [class*="dismiss"], [id*="dismiss"]');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            if (el.disabled) el.disabled = false;
                            var text = (el.textContent || el.innerText || '').toLowerCase();
                            var isSkipBtn = text.indexOf('skip') !== -1 || text.indexOf('close') !== -1 || text.indexOf('dismiss') !== -1 || text.indexOf('continue') !== -1 || text.indexOf('lewati') !== -1 || text.indexOf('tutup') !== -1;
                            if (isSkipBtn) {
                                // Only make visible/clickable, do NOT auto-click
                                // Auto-clicking triggers unwanted site-side handlers
                                el.style.setProperty('display', 'block', 'important');
                                el.style.setProperty('visibility', 'visible', 'important');
                                el.style.setProperty('opacity', '1', 'important');
                                el.style.setProperty('pointer-events', 'auto', 'important');
                                el.disabled = false;
                                el.style.removeProperty('pointer-events');
                                el.removeAttribute('disabled');
                                el.removeAttribute('aria-disabled');
                            }
                        }
                    } catch(e) {}
                }

                function removeAdIframes() {
                    try {
                        var iframes = document.getElementsByTagName('iframe');
                        for (var j = iframes.length - 1; j >= 0; j--) {
                            var iframe = iframes[j];
                            try {
                                var rect = iframe.getBoundingClientRect();
                                var pos = window.getComputedStyle(iframe).position;
                                var src = (iframe.src || '').toLowerCase();
                                // Skip iframes that contain video players
                                if (iframe.querySelector && iframe.querySelector('video, source, canvas')) continue;
                                if ((!src || src === 'about:blank') && rect.width > 100 && rect.height > 100) {
                                    removeAd(iframe);
                                    continue;
                                }
                                // Skip large iframes - likely video player
                                if (rect.width > window.innerWidth * 0.6) continue;
                                if (rect.height > window.innerHeight * 0.6) continue;
                                if (rect.width > window.innerWidth * 0.4 && rect.height > window.innerHeight * 0.3) {
                                    if (pos === 'fixed' || pos === 'absolute') {
                                        removeAd(iframe);
                                        continue;
                                    }
                                }
                                if (hasAdUrl(iframe)) removeAd(iframe);
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                function removeAdContainers() {
                    try {
                        var selectors = [
                            'div[id*="div-gpt"]', 'div[class*="div-gpt"]',
                            'div[id*="google_ads"]', 'div[class*="google_ads"]',
                            'ins.adsbygoogle', 'ins[class*="adsbygoogle"]',
                            'div[data-google-query-id]',
                            'amp-ad', 'amp-embed[type*="taboola"]',
                            'div[id*="taboola"]', 'div[class*="taboola"]',
                            'div[id*="outbrain"]', 'div[class*="outbrain"]',
                            'div[class*="ad-container"]', 'div[class*="ad-wrapper"]',
                            'div[class*="ad-slot"]', 'div[class*="ad-unit"]',
                            'div[class*="ad-banner"]', 'div[class*="ad-placement"]',
                            'div[class*="advertisement"]', 'div[class*="advert"]',
                            'div[class*="sponsored-content"]', 'div[class*="promoted-content"]',
                            'div[class*="sticky-footer"]', 'div[class*="footer-ad"]',
                            'div[class*="bottom-banner"]', 'div[class*="top-banner"]',
                            'div[class*="floating-bar"]', 'div[class*="floating-ad"]',
                        ];
                        for (var si = 0; si < selectors.length; si++) {
                            try {
                                var els = document.querySelectorAll(selectors[si]);
                                for (var ei = 0; ei < els.length; ei++) {
                                    removeAd(els[ei]);
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                function breakAdTimers() {
                    try {
                        var origSetTimeout = window.setTimeout;
                        var origSetInterval = window.setInterval;
                        var adKeywordPattern = /(?:ad[s]?[-\s]|advert|sponsor|popup|overlay|banner|interstitial|countdown|skip[-\s]?(?:ad|button|btn)?|close[-\s]?(?:ad|banner|popup)?|dismiss[-\s]?(?:ad)?|timer[-\s]?(?:ad)?)/i;

                        window.setTimeout = function(fn, delay) {
                            if (delay > 0 && delay <= 30000 && typeof fn === 'function') {
                                // Check function source for ad-related keywords
                                var fnStr = fn.toString();
                                if (adKeywordPattern.test(fnStr)) {
                                    // Check if the function accesses DOM elements with ad-related classes/ids
                                    // by checking if it references known ad patterns
                                    var hasAdDomRef = /document\.(?:getElementById|querySelector|querySelectorAll|getElementsBy(?:ClassName|TagName))/.test(fnStr) &&
                                        /(?:ad[s]?[-\s]|advert|sponsor|popup|overlay|banner|interstitial|skip[-\s]?ad|close[-\s]?ad)/i.test(fnStr);
                                    if (hasAdDomRef) {
                                        return origSetTimeout(function(){}, 86400000);
                                    }
                                    // For non-DOM ad timers (like countdown), delay significantly
                                    if (/(?:countdown|timer)/i.test(fnStr) && !/document\./.test(fnStr)) {
                                        return origSetTimeout(function(){}, 86400000);
                                    }
                                }
                            }
                            return origSetTimeout.apply(this, arguments);
                        };

                        window.setInterval = function(fn, delay) {
                            if (typeof fn === 'function') {
                                var fnStr = fn.toString();
                                if (adKeywordPattern.test(fnStr)) {
                                    // Only block if it references ad DOM elements
                                    var hasAdDomRef = /document\.(?:getElementById|querySelector|querySelectorAll|getElementsBy(?:ClassName|TagName))/.test(fnStr) &&
                                        /(?:ad[s]?[-\s]|advert|sponsor|popup|overlay|banner|interstitial)/i.test(fnStr);
                                    if (hasAdDomRef) {
                                        return origSetInterval(function(){}, 86400000);
                                    }
                                }
                            }
                            return origSetInterval.apply(this, arguments);
                        };
                    } catch(e) {}
                }

                function killOverflowHiding() {
                    try {
                        if (window.__yue_in_fullscreen__) return;
                        if (document.body) {
                            if (document.body.style.overflow === 'hidden') document.body.style.overflow = 'visible';
                            document.body.style.setProperty('overflow', 'visible', 'important');
                        }
                        if (document.documentElement) {
                            if (document.documentElement.style.overflow === 'hidden') document.documentElement.style.overflow = 'visible';
                            document.documentElement.style.setProperty('overflow', 'visible', 'important');
                        }
                    } catch(e) {}
                }

                function removeFixedOverlays() {
                    try {
                        if (window.__yue_in_fullscreen__) return;
                        var all = document.querySelectorAll('*');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            try {
                                var style = window.getComputedStyle(el);
                                if (style.position === 'fixed' || style.position === 'absolute') {
                                    var zIdx = parseInt(style.zIndex) || 0;
                                    if (zIdx > 900) {
                                        var rect = el.getBoundingClientRect();
                                        if (rect.width > window.innerWidth * 0.5 && rect.height > window.innerHeight * 0.5) {
                                            var tag = el.tagName.toLowerCase();
                                            if (tag !== 'nav' && tag !== 'header' && tag !== 'footer' && tag !== 'dialog') {
                                                removeAd(el);
                                            }
                                        }
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                function scanAndRemove() {
                    try {
                        // Skip scanning when in fullscreen to avoid flicker
                        if (window.__yue_in_fullscreen__) return;
                        var root = document.documentElement || document.body;
                        if (!root) return;

                        var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, {
                            acceptNode: function(node) {
                                if (isLikelyAd(node)) return NodeFilter.FILTER_ACCEPT;
                                return NodeFilter.FILTER_SKIP;
                            }
                        });

                        var toRemove = [];
                        var current;
                        while ((current = walker.nextNode())) {
                            toRemove.push(current);
                        }

                        for (var i = 0; i < toRemove.length; i++) {
                            removeAd(toRemove[i]);
                        }
                        removeAdIframes();
                        removeAdContainers();
                        removeFixedOverlays();
                        forceEnableSkip();
                        killOverflowHiding();
                    } catch(e) {}
                }

                breakAdTimers();
                killOverflowHiding();
                scanAndRemove();

                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    setTimeout(scanAndRemove, 50);
                    setTimeout(scanAndRemove, 100);
                    setTimeout(scanAndRemove, 200);
                    setTimeout(scanAndRemove, 500);
                    setTimeout(scanAndRemove, 1000);
                    setTimeout(scanAndRemove, 2000);
                    setTimeout(scanAndRemove, 3000);
                    setTimeout(scanAndRemove, 5000);
                    setTimeout(scanAndRemove, 8000);
                    setTimeout(scanAndRemove, 12000);
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        scanAndRemove();
                        setTimeout(scanAndRemove, 100);
                        setTimeout(scanAndRemove, 300);
                        setTimeout(scanAndRemove, 800);
                        setTimeout(scanAndRemove, 1500);
                        setTimeout(scanAndRemove, 3000);
                        setTimeout(scanAndRemove, 5000);
                        setTimeout(scanAndRemove, 8000);
                        setTimeout(scanAndRemove, 12000);
                    });
                }

                if (!window.__yueAdObserver) {
                    window.__yueAdObserver = new MutationObserver(function(mutations) {
                        try {
                            var needsScan = false;
                            for (var i = 0; i < mutations.length; i++) {
                                var added = mutations[i].addedNodes;
                                for (var j = 0; j < added.length; j++) {
                                    var node = added[j];
                                    if (node.nodeType === 1 && isLikelyAd(node)) {
                                        removeAd(node);
                                        needsScan = true;
                                    }
                                    if (node.querySelectorAll) {
                                        var matches = node.querySelectorAll('[class*="ad"],[class*="Ad"],[class*="sponsor"],[class*="popup"],[data-ad],iframe');
                                        for (var k = 0; k < matches.length; k++) {
                                            if (isLikelyAd(matches[k])) removeAd(matches[k]);
                                        }
                                    }
                                }
                            }
                            if (needsScan) {
                                forceEnableSkip();
                                killOverflowHiding();
                            }
                        } catch(e) {}
                    });
                    if (document.documentElement) {
                        window.__yueAdObserver.observe(document.documentElement, {
                            childList: true, subtree: true
                        });
                    }
                }

                if (!window.__yueRescanTimer) {
                    window.__yueRescanTimer = setInterval(function() {
                        try { scanAndRemove(); } catch(e) {}
                    }, 200);
                    setTimeout(function() {
                        if (window.__yueRescanTimer) {
                            clearInterval(window.__yueRescanTimer);
                            window.__yueRescanTimer = setInterval(function() {
                                try { scanAndRemove(); } catch(e) {}
                            }, 500);
                        }
                    }, 10000);
                }

                if (!window.__yueOverflowTimer) {
                    window.__yueOverflowTimer = setInterval(function() {
                        try { killOverflowHiding(); } catch(e) {}
                    }, 1000);
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }
}