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
        "interstitial", "lightbox-ad", "modal-ad",
        "exit-intent", "entry-ad",
        "ad-frame", "adFrame", "ad_frame",
        "google-ad", "googleAd", "doubleclick",
        "adsense", "adserver", "adtech",
        "taboola", "outbrain", "mgid", "contentad",
        "revcontent", "zemanta",
        "ad-slot", "adSlot",
        "ad-container", "adContainer",
        "ad-wrapper", "adWrapper", "ads-wrapper",
        "promoted-content", "sponsored-content",
        "betting", "casino", "gambling", "slot-",
        "video-ad", "videoAd", "pre-roll", "preroll",
        "div-gpt-ad", "adsbygoogle",
        "ads-overlay", "ad-overlay",
        "floating-bar", "sticky-footer", "fixed-footer",
        "ad-footer", "footer-ad",
        "bottom-banner", "top-banner", "notification-bar",
        "close-ad", "close-banner",
        "paywall-overlay", "paywall", "subscription-wall",
        "dark-overlay", "overlay-dark",
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
                var adblockCss = $quotedCss;

                // Phase 0: Tag popup/overlay elements BEFORE CSS injection
                // This ensures they can be protected from adblock CSS
                function tagPopups() {
                    try {
                        var all = document.querySelectorAll('*');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            if (el.nodeType !== 1) continue;
                            var tag = el.tagName ? el.tagName.toLowerCase() : '';
                            if (tag === 'html' || tag === 'body' || tag === 'head' || tag === 'script' || tag === 'style') continue;
                            try {
                                var rect = el.getBoundingClientRect();
                                if (rect.width < 100 || rect.height < 50) continue;
                                var style = window.getComputedStyle(el);
                                var pos = style.position;
                                if (pos !== 'fixed' && pos !== 'absolute' && pos !== 'sticky') continue;
                                var viewW = window.innerWidth || document.documentElement.clientWidth;
                                var viewH = window.innerHeight || document.documentElement.clientHeight;
                                var areaRatio = (rect.width * rect.height) / (viewW * viewH);
                                // Protect elements covering >20% of viewport with interactive children
                                if (areaRatio > 0.2) {
                                    var hasInteractive = false;
                                    if (el.querySelector) {
                                        var kids = el.querySelector('button, a, input, [role="button"], select, textarea, [type="text"], [type="email"], [type="password"]');
                                        if (kids) hasInteractive = true;
                                    }
                                    if (hasInteractive) {
                                        el.setAttribute('data-yue-popup', '1');
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }
                tagPopups();

                // Phase 1: Inject adblock CSS
                function injectStyle() {
                    try {
                        if (document.head) {
                            var style = document.getElementById('yue-adblock-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'yue-adblock-style';
                                style.textContent = adblockCss;
                                document.head.appendChild(style);
                            } else if (style.textContent !== adblockCss) {
                                style.textContent = adblockCss;
                            }
                        }
                    } catch(e) {}
                }
                injectStyle();

                // Phase 2: Inject popup protection CSS with HIGHER specificity
                // This MUST come AFTER adblock CSS to override it
                function injectPopupProtection() {
                    try {
                        if (document.head) {
                            var protectStyle = document.getElementById('yue-popup-protect');
                            if (!protectStyle) {
                                protectStyle = document.createElement('style');
                                protectStyle.id = 'yue-popup-protect';
                                protectStyle.textContent = [
                                    '/* Yue Popup Protection — overrides adblock for consent/legitimate popups */',
                                    'html > body > [data-yue-popup="1"],',
                                    'body > [data-yue-popup="1"],',
                                    '[data-yue-popup="1"][role="dialog"],',
                                    'div[data-yue-popup="1"] {',
                                    '  display: initial !important;',
                                    '  visibility: visible !important;',
                                    '  opacity: 1 !important;',
                                    '  max-height: none !important;',
                                    '  max-width: none !important;',
                                    '  height: auto !important;',
                                    '  width: auto !important;',
                                    '  overflow: visible !important;',
                                    '  transform: none !important;',
                                    '  z-index: 2147483647 !important;',
                                    '  clip: auto !important;',
                                    '  clip-path: none !important;',
                                    '  pointer-events: auto !important;',
                                    '  position: fixed !important;',
                                    '  top: 0 !important;',
                                    '  left: 0 !important;',
                                    '  right: 0 !important;',
                                    '  bottom: 0 !important;',
                                    '  background: initial !important;',
                                    '  backdrop-filter: none !important;',
                                    '}',
                                    '/* Also protect children of popups from being hidden */',
                                    '[data-yue-popup="1"] *,',
                                    '[data-yue-popup="1"] *::before,',
                                    '[data-yue-popup="1"] *::after {',
                                    '  display: revert !important;',
                                    '  visibility: visible !important;',
                                    '  opacity: 1 !important;',
                                    '  max-height: none !important;',
                                    '  max-width: none !important;',
                                    '  overflow: visible !important;',
                                    '  pointer-events: auto !important;',
                                    '}'
                                ].join('\\n');
                                document.head.appendChild(protectStyle);
                            }
                        }
                    } catch(e) {}
                }
                injectPopupProtection();

                // Re-tag and re-protect when DOM changes
                if (!window.yueCssObserver) {
                    window.yueCssObserver = new MutationObserver(function(m) {
                        try {
                            tagPopups();
                            injectPopupProtection();
                        } catch(e) {}
                    });
                    if (document.documentElement) {
                        window.yueCssObserver.observe(document.documentElement, {
                            childList: true, subtree: true
                        });
                    }
                }
                // Also re-tag on load milestones
                document.addEventListener('DOMContentLoaded', function() {
                    tagPopups();
                    injectPopupProtection();
                });
                window.addEventListener('load', function() {
                    tagPopups();
                    injectPopupProtection();
                    setTimeout(function() { tagPopups(); injectPopupProtection(); }, 500);
                    setTimeout(function() { tagPopups(); injectPopupProtection(); }, 2000);
                });
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun buildCombinedScript(css: String): String {
        val cssScript = if (css.isNotBlank()) buildCssInjectionScript(css) else ""
        val protectorScript = buildConsentProtectorScript()
        val aggressiveScript = buildAggressiveAdRemovalScript()
        return """
        (function() {
            try {
                // Phase 1: CSS injection (full coverage)
                $cssScript
            } catch(e) {}
            try {
                // Phase 2: Consent protector — restores consent/legitimate elements hidden by CSS
                $protectorScript
            } catch(e) {}
            try {
                // Phase 3: JS-based ad detection & removal
                $aggressiveScript
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun buildConsentProtectorScript(): String {
        return """
        (function() {
            try {
                // Consent/agreement related class/id substrings
                var CONSENT_TOKENS = [
                    'cookie-banner', 'cookiebanner', 'cookie-banner-container',
                    'gdpr-modal', 'gdpr-banner', 'gdpr-consent',
                    'consent-banner', 'consent-modal', 'consent-container',
                    'cookie-consent', 'cookie-notice', 'cookienotice',
                    'onetrust', 'cookiebot', 'klaro', 'optanon',
                    'trustarc', 'truste', 'osano',
                    'agreement-modal', 'agreement-popup', 'agreement-banner',
                    'terms-modal', 'terms-popup', 'terms-banner',
                    'verification-modal', 'verification-popup',
                    'human-verification', 'bot-check',
                    'privacy-modal', 'privacy-popup',
                ];

                function cleanStyleSheet(styleEl) {
                    try {
                        if (!styleEl || !styleEl.sheet) return;
                        var sheet = styleEl.sheet;
                        var rules = sheet.cssRules || sheet.rules;
                        if (!rules) return;
                        // Iterate in reverse so deleting doesn't break indices
                        for (var i = rules.length - 1; i >= 0; i--) {
                            var rule = rules[i];
                            var selectorText = (rule.selectorText || '').toLowerCase();
                            if (!selectorText) continue;
                            var shouldRemove = false;
                            for (var j = 0; j < CONSENT_TOKENS.length; j++) {
                                if (selectorText.indexOf(CONSENT_TOKENS[j]) !== -1) {
                                    shouldRemove = true;
                                    break;
                                }
                            }
                            if (shouldRemove) {
                                try { sheet.deleteRule(i); } catch(e) {}
                            }
                        }
                    } catch(e) {}
                }

                function removeConsentRulesFromAllStyles() {
                    // Find the Yue-adblock style tag
                    var yueStyle = document.getElementById('yue-adblock-style');
                    if (yueStyle) {
                        cleanStyleSheet(yueStyle);
                        // Also clean up: set display to empty for consent elements
                        // (in case they were already hidden before CSS cleanup)
                        restoreVisibleConsent();
                    }
                    // Also clean any other style tags that might have consent rules
                    var allStyles = document.querySelectorAll('style');
                    for (var s = 0; s < allStyles.length; s++) {
                        var st = allStyles[s];
                        var id = st.id || '';
                        var sc = (st.textContent || '').toLowerCase();
                        // Only process styles that might have consent rules
                        for (var k = 0; k < CONSENT_TOKENS.length; k++) {
                            if (sc.indexOf(CONSENT_TOKENS[k]) !== -1) {
                                cleanStyleSheet(st);
                                break;
                            }
                        }
                    }
                }

                function restoreVisibleConsent() {
                    var consentSelector = CONSENT_TOKENS.map(function(t) {
                        return '[class*="' + t + '"], [id*="' + t + '"]';
                    }).join(',');
                    try {
                        var els = document.querySelectorAll(consentSelector);
                        for (var i = 0; i < els.length; i++) {
                            var el = els[i];
                            var rect = el.getBoundingClientRect();
                            if (rect.width > 0 && rect.height > 0) {
                                // Force restore by clearing inline hiding
                                el.style.setProperty('display', 'initial', 'important');
                                el.style.setProperty('visibility', 'visible', 'important');
                                el.style.setProperty('opacity', '1', 'important');
                                el.style.removeProperty('max-height');
                                el.style.removeProperty('max-width');
                                el.style.removeProperty('height');
                                el.style.removeProperty('width');
                                el.style.removeProperty('overflow');
                                // Restore parent containers (up to 3 levels)
                                var p = el.parentElement;
                                var d = 0;
                                while (p && p !== document.body && d < 3) {
                                    p.style.setProperty('display', 'initial', 'important');
                                    p.style.setProperty('visibility', 'visible', 'important');
                                    p.style.setProperty('opacity', '1', 'important');
                                    p.style.removeProperty('max-height');
                                    p.style.removeProperty('overflow');
                                    p = p.parentElement;
                                    d++;
                                }
                            }
                        }
                    } catch(e) {}

                    // Also restore any dialog/modal with interactive children
                    try {
                        var dialogs = document.querySelectorAll('[role="dialog"], [aria-modal="true"]');
                        for (var j = 0; j < dialogs.length; j++) {
                            var dlg = dialogs[j];
                            var dr = dlg.getBoundingClientRect();
                            if (dr.width > 50 && dr.height > 30) {
                                dlg.style.setProperty('display', 'initial', 'important');
                                dlg.style.setProperty('visibility', 'visible', 'important');
                                dlg.style.setProperty('opacity', '1', 'important');
                                dlg.style.removeProperty('max-height');
                                dlg.style.removeProperty('overflow');
                            }
                        }
                    } catch(e) {}
                }

                // Run immediately
                removeConsentRulesFromAllStyles();

                // Run again after DOM ready
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        removeConsentRulesFromAllStyles();
                    });
                }
                document.addEventListener('load', function() {
                    removeConsentRulesFromAllStyles();
                    setTimeout(removeConsentRulesFromAllStyles, 1000);
                    setTimeout(removeConsentRulesFromAllStyles, 3000);
                });

                // MutationObserver: re-clean when styles are added/modified
                if (!window.__yueConsentProtector) {
                    window.__yueConsentProtector = new MutationObserver(function(mutations) {
                        var needClean = false;
                        for (var mi = 0; mi < mutations.length; mi++) {
                            var m = mutations[mi];
                            if (m.addedNodes) {
                                for (var ai = 0; ai < m.addedNodes.length; ai++) {
                                    var node = m.addedNodes[ai];
                                    if (node.nodeType === 1 && node.tagName === 'STYLE') {
                                        needClean = true;
                                        break;
                                    }
                                }
                            }
                            if (m.type === 'attributes' && m.attributeName === 'style') needClean = true;
                        }
                        if (needClean) {
                            clearTimeout(window.__yueConsentProtectorTimer);
                            window.__yueConsentProtectorTimer = setTimeout(removeConsentRulesFromAllStyles, 200);
                        }
                    });
                    if (document.documentElement) {
                        window.__yueConsentProtector.observe(document.documentElement, {
                            childList: true, subtree: true,
                            attributes: true, attributeFilter: ['style']
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
                    var adTexts = ['advertisement', 'sponsored', 'ad choice',
                        'ad by', 'powered by', 'brought to you by', 'supported by',
                        'iklankan', 'sponsor', 'iklan', 'promosi',
                        'skip ad', 'skip in', 'ad will close', 'ad ends',
                        'close ad', 'close this ad', 'dismiss ad',
                        'ad blocker', 'please disable', 'disable adblock'];
                    for (var i = 0; i < adTexts.length; i++) {
                        if (text.indexOf(adTexts[i]) !== -1) return true;
                    }
                    return false;
                }

                function hasConsentSignals(el) {
                    if (!el) return false;
                    var cls = (el.className || '').toString().toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                    var ariaModal = (el.getAttribute && el.getAttribute('aria-modal') || '').toLowerCase();
                    var consentSignals = ['consent', 'agreement', 'terms', 'privacy', 'cookie-banner', 'cookie-consent',
                        'gdpr', 'ccpa', 'optanon', 'onetrust', 'cookiebot', 'trustarc', 'truste', 'osano', 'klaro',
                        'cookie-notice', 'CookieNotice', 'CookieBanner'];
                    if (role === 'dialog' || ariaModal === 'true') return true;
                    for (var i = 0; i < consentSignals.length; i++) {
                        if (cls.indexOf(consentSignals[i]) !== -1 || id.indexOf(consentSignals[i]) !== -1) return true;
                    }
                    return false;
                }

                function isLegitimatePopup(el) {
                    if (!el || !el.querySelector) return false;

                    // Must be a LARGE overlay (not a small ad card inline)
                    var rect;
                    try { rect = el.getBoundingClientRect(); } catch(e) { return false; }
                    if (rect.width < window.innerWidth * 0.3 && rect.height < window.innerHeight * 0.3) return false;

                    // Must be positioned as overlay (fixed or absolute)
                    var style = window.getComputedStyle(el);
                    if (style.position !== 'fixed' && style.position !== 'absolute') return false;

                    // Must have interactive form elements (inputs, forms — not just any button)
                    var hasForm = el.querySelector('form, input, select, textarea, [type="text"], [type="email"], [type="password"]');
                    if (!hasForm) return false;

                    // Must have agreement/consent text content
                    var text = (el.textContent || '').toLowerCase().trim();
                    var hasAgreeText = false;
                    if (text) {
                        var legitimacySignals = ['agree', 'accept', 'terms', 'agreement',
                            'privacy policy', 'cookie policy', 'consent',
                            'human verification', 'verify you', 'i am not a robot'];
                        var checkText = text.substring(0, Math.min(text.length, 500));
                        for (var i = 0; i < legitimacySignals.length; i++) {
                            if (checkText.indexOf(legitimacySignals[i]) !== -1) {
                                hasAgreeText = true;
                                break;
                            }
                        }
                    }
                    if (!hasAgreeText) return false;

                    // Must NOT have ad-related class/id signals
                    var cls = (el.className || '').toString().toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var adSignals = ['ad-', 'ad_', 'ads-', 'ads_', 'sponsor', 'promo',
                        'promotion', 'advertisement', 'ad-slot', 'ad-container',
                        'popup-ad', 'overlay-ad', 'floating-ad', 'sticky-ad'];
                    for (var j = 0; j < adSignals.length; j++) {
                        if (cls.indexOf(adSignals[j]) !== -1 || id.indexOf(adSignals[j]) !== -1) return false;
                    }

                    return true;
                }

                function isLikelyAd(el) {
                    if (!el || !el.parentElement) return false;
                    if (el.nodeType !== 1) return false;
                    var tag = el.tagName ? el.tagName.toLowerCase() : '';
                    if (tag === 'html' || tag === 'body' || tag === 'head' || tag === 'title' || tag === 'meta' || tag === 'script' || tag === 'style' || tag === 'link') return false;

                    // Never block popup/overlay elements protected by Phase 0
                    if (el.hasAttribute && el.hasAttribute('data-yue-popup')) return false;

                    // Never block consent/agreement popups (check self only)
                    if (hasConsentSignals(el)) return false;
                    if (isLegitimatePopup(el)) return false;

                    // Check ancestors — 3 levels only (not to body)
                    var parent = el.parentElement;
                    var depth = 0;
                    while (parent && parent !== document.body && depth < 3) {
                        if (parent.hasAttribute && parent.hasAttribute('data-yue-popup')) return false;
                        if (hasConsentSignals(parent)) return false;
                        if (isLegitimatePopup(parent)) return false;
                        parent = parent.parentElement;
                        depth++;
                    }

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
                        // Never hide popup/overlay elements protected by Phase 0
                        if (el.hasAttribute && el.hasAttribute('data-yue-popup')) return;
                        // Never hide consent/agreement popups (check self first)
                        if (hasConsentSignals(el)) return;
                        if (isLegitimatePopup(el)) return;

                        // Check ancestors — 3 levels only (not to body)
                        var parent = el.parentElement;
                        var depth = 0;
                        while (parent && parent !== document.body && depth < 3) {
                            if (parent.hasAttribute && parent.hasAttribute('data-yue-popup')) return;
                            if (hasConsentSignals(parent)) return;
                            if (isLegitimatePopup(parent)) return;
                            parent = parent.parentElement;
                            depth++;
                        }

                        // Use minimal CSS — only hide visually, don't break layout
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('opacity', '0', 'important');
                        // Do NOT set pointer-events:none — breaks consent/agreement buttons
                        // Do NOT set height/width to 0 — causes layout shifts & blinking
                        // Do NOT use position:absolute + top:-99999 — causes reflow
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
                                // Skip consent/agreement popups
                                if (hasConsentSignals(el)) continue;
                                if (isLegitimatePopup(el)) continue;

                                // Check ancestors — 3 levels only
                                var anc = el.parentElement;
                                var adepth = 0;
                                var skip = false;
                                while (anc && anc !== document.body && adepth < 3) {
                                    if (hasConsentSignals(anc) || isLegitimatePopup(anc)) { skip = true; break; }
                                    anc = anc.parentElement;
                                    adepth++;
                                }
                                if (skip) continue;

                                var style = window.getComputedStyle(el);
                                if (style.position === 'fixed' || style.position === 'absolute') {
                                    var zIdx = parseInt(style.zIndex) || 0;
                                    if (zIdx > 900) {
                                        var rect = el.getBoundingClientRect();
                                        if (rect.width > window.innerWidth * 0.5 && rect.height > window.innerHeight * 0.5) {
                                            var tag = el.tagName.toLowerCase();
                                            if (tag !== 'nav' && tag !== 'header' && tag !== 'footer' && tag !== 'dialog') {
                                                // Only hide if it has ad-related class/id signals
                                                var cls = (el.className || '').toString().toLowerCase();
                                                var id = (el.id || '').toLowerCase();
                                                var hasAdSignals = cls.indexOf('ad-') !== -1 || cls.indexOf('ad_') !== -1 ||
                                                    cls.indexOf('sponsor') !== -1 || cls.indexOf('promo') !== -1 ||
                                                    cls.indexOf('popup-ad') !== -1 || cls.indexOf('overlay-ad') !== -1 ||
                                                    id.indexOf('ad-') !== -1 || id.indexOf('ad_') !== -1;
                                                if (hasAdSignals) {
                                                    removeAd(el);
                                                }
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

                // Reduced scan schedule: only 3 follow-up scans (was 10+)
                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    setTimeout(scanAndRemove, 800);
                    setTimeout(scanAndRemove, 3000);
                    setTimeout(scanAndRemove, 8000);
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        scanAndRemove();
                        setTimeout(scanAndRemove, 1000);
                        setTimeout(scanAndRemove, 4000);
                        setTimeout(scanAndRemove, 9000);
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
                    // Slower rescan: 1.5s initial, then 5s — avoids layout thrashing
                    window.__yueRescanTimer = setInterval(function() {
                        try { scanAndRemove(); } catch(e) {}
                    }, 1500);
                    setTimeout(function() {
                        if (window.__yueRescanTimer) {
                            clearInterval(window.__yueRescanTimer);
                            window.__yueRescanTimer = setInterval(function() {
                                try { scanAndRemove(); } catch(e) {}
                            }, 5000);
                        }
                    }, 15000);
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