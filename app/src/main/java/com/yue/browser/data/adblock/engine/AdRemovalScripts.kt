package com.yue.browser.data.adblock.engine

import org.json.JSONObject

object AdRemovalScripts {

    val adClassFragments = arrayOf(
        // EXPLICIT ad container classes (hanya yang sangat spesifik iklan, bukan feature situs normal)
        "advertisement", "advertise",
        "sponsored-content", "sponsoredContent",
        // Overlay/popup ad patterns (spesifik ad, bukan dialog/modal normal)
        "popup-ad", "popupAd", "popunder", "popin", "popout", "pop-up-ad",
        "overlay-ad", "overlayAd", "floating-ad", "floatingAd",
        "sticky-ad", "stickyAd", "banner-ad", "bannerAd",
        "top-ad", "topAd", "bottom-ad", "bottomAd",
        "side-ad", "sideAd", "sidebar-ad",
        "interstitial", "lightbox-ad", "modal-ad",
        "exit-intent", "entry-ad",
        "ad-frame", "adFrame",
        "google-ad", "googleAd", "doubleclick-ad",
        "adsense", "AdSense", "Adsense",
        "adserver", "adtech",
        "taboola", "outbrain", "mgid", "contentad",
        "revcontent", "zemanta",
        "ad-slot", "adSlot",
        "ad-container", "adContainer",
        "ad-wrapper", "adWrapper", "ads-wrapper",
        // Video ad (spesifik iklan video)
        "video-ad", "videoAd", "pre-roll-ad", "preroll-ad",
        // Google Publisher Tags
        "div-gpt-ad", "adsbygoogle",
        // Ad overlay patterns
        "ads-overlay", "ad-overlay",
        "ad-footer", "footer-ad",
        "bottom-banner-ad", "top-banner-ad",
        // Close ad buttons (spesifik iklan)
        "close-ad", "close-banner-ad",
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
                        // First: directly tag semantic dialog/modal elements — no heuristic needed
                        var dialogSelectors = '[role="dialog"], [role="alertdialog"], [aria-modal="true"], dialog';
                        var dialogEls = document.querySelectorAll(dialogSelectors);
                        for (var d = 0; d < dialogEls.length; d++) {
                            if (!dialogEls[d].hasAttribute('data-yue-popup')) {
                                dialogEls[d].setAttribute('data-yue-popup', '1');
                            }
                        }

                        // Second: scan large overlays with interactive children (heuristic)
                        var all = document.querySelectorAll('*');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            if (el.nodeType !== 1) continue;
                            if (el.hasAttribute && el.hasAttribute('data-yue-popup')) continue;
                            var tag = el.tagName ? el.tagName.toLowerCase() : '';
                            if (tag === 'html' || tag === 'body' || tag === 'head' || tag === 'script' || tag === 'style') continue;
                            try {
                                var rect = el.getBoundingClientRect();
                                if (rect.width < 100 || rect.height < 50) continue;
                                var style = window.getComputedStyle(el);
                                var pos = style.position;
                                // Allow fixed/absolute/sticky overlays
                                if (pos !== 'fixed' && pos !== 'absolute' && pos !== 'sticky') continue;
                                var viewW = window.innerWidth || document.documentElement.clientWidth;
                                var viewH = window.innerHeight || document.documentElement.clientHeight;
                                var areaRatio = (rect.width * rect.height) / (viewW * viewH);
                                // Protect elements covering >10% of viewport (lowered from 20%)
                                if (areaRatio > 0.1) {
                                    var hasInteractive = false;
                                    if (el.querySelector) {
                                        var kids = el.querySelector('button, a, [role="button"], [role="link"], select, textarea');
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
                                    '/* Yue Popup Protection — protects consent/legitimate popups from adblock CSS */',
                                    '/* SAFETY: never override display/position/size — that breaks site layout (flex/grid/centering) */',
                                    '[data-yue-popup="1"],',
                                    '[role="dialog"],',
                                    '[aria-modal="true"] {',
                                    '  visibility: visible !important;',
                                    '  opacity: 1 !important;',
                                    '  pointer-events: auto !important;',
                                    '  clip: auto !important;',
                                    '  clip-path: none !important;',
                                    '}',
                                    '/* Protect children of popups from being hidden — visibility/opacity only */',
                                    '[data-yue-popup="1"] *,',
                                    '[role="dialog"] *,',
                                    '[aria-modal="true"] * {',
                                    '  visibility: visible !important;',
                                    '  opacity: 1 !important;',
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
                    'fundingchoices', 'funding-choices', 'google-funding-choices',
                    'tcf', 'tcf-v2', 'iab-tcf',
                    'gpc', 'global-privacy-control',
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
                    // ONLY clean the Yue-adblock injected style tag
                    // NEVER touch style tags belonging to the site itself — that deletes
                    // legitimate site CSS rules (e.g. if a site container has 'consent' in class)
                    var yueStyle = document.getElementById('yue-adblock-style');
                    if (yueStyle) {
                        cleanStyleSheet(yueStyle);
                        // Restore visibility of consent elements hidden before cleanup
                        restoreVisibleConsent();
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
                                // Restore by REMOVING our inline hiding — never force display values (breaks layout)
                                el.style.removeProperty('display');
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
                                    p.style.removeProperty('display');
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
                                dlg.style.removeProperty('display');
                                dlg.style.setProperty('visibility', 'visible', 'important');
                                dlg.style.setProperty('opacity', '1', 'important');
                                dlg.style.removeProperty('max-height');
                                dlg.style.removeProperty('overflow');
                            }
                        }
                    } catch(e) {}

                    // KEY: Also scan elements by CONSENT TEXT content — catches inline popups with generic classes
                    try {
                        var textSignals = ['cookie', 'cookies', 'cookie policy', 'privacy policy',
                            'consent', 'preferences', 'manage preferences', 'data collection',
                            'third party', 'data partners',
                            'pusat preferensi', 'kelola preferensi', 'kebijakan cookie',
                            'kebijakan privasi', 'verifikasi manusia', 'persyaratan'];
                        var skipTags = {'html':1,'body':1,'head':1,'script':1,'style':1,'title':1,'meta':1,'link':1};
                        var root = document.documentElement || document.body;
                        if (root) {
                            var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, {
                                acceptNode: function(node) {
                                    var t = node.tagName ? node.tagName.toLowerCase() : '';
                                    if (skipTags[t]) return NodeFilter.FILTER_SKIP;
                                    if (node.hasAttribute && node.hasAttribute('data-yue-popup')) return NodeFilter.FILTER_SKIP;
                                    return NodeFilter.FILTER_ACCEPT;
                                }
                            });
                            var candidates = [];
                            var cn;
                            while ((cn = walker.nextNode())) {
                                var text = (cn.textContent || '').toLowerCase().trim();
                                if (!text || text.length < 50) continue;
                                var matches = 0;
                                for (var k = 0; k < textSignals.length; k++) {
                                    if (text.indexOf(textSignals[k]) !== -1) matches++;
                                }
                                // Require at least 2 text signal matches to avoid false positives
                                if (matches >= 2) {
                                    // Verify it has interactive elements
                                    if (cn.querySelector && cn.querySelector('button, a, [role="button"]')) {
                                        candidates.push(cn);
                                    }
                                }
                            }
                            for (var c = 0; c < candidates.length; c++) {
                                var cel = candidates[c];
                                cel.style.removeProperty('display');
                                cel.style.setProperty('visibility', 'visible', 'important');
                                cel.style.setProperty('opacity', '1', 'important');
                                cel.style.removeProperty('max-height');
                                cel.style.removeProperty('overflow');
                                cel.setAttribute('data-yue-popup', '1');
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
                        'cookie-notice', 'CookieNotice', 'CookieBanner',
                        'fundingchoices', 'funding-choices', 'google-consent'];
                    if (role === 'dialog' || ariaModal === 'true') return true;
                    for (var i = 0; i < consentSignals.length; i++) {
                        if (cls.indexOf(consentSignals[i]) !== -1 || id.indexOf(consentSignals[i]) !== -1) return true;
                    }
                    // ALSO check text content — popup consent inline sering punya class generic
                    var text = (el.textContent || '').toLowerCase().trim();
                    if (text && text.length >= 30) {
                        var textSignals = ['cookie', 'cookies', 'cookie policy', 'privacy policy',
                            'consent', 'preferences', 'manage preferences',
                            'third party', 'data partners',
                            'pusat preferensi', 'kelola preferensi', 'kebijakan cookie',
                            'kebijakan privasi', 'verifikasi manusia'];
                        for (var j = 0; j < textSignals.length; j++) {
                            if (text.indexOf(textSignals[j]) !== -1) {
                                // Only confirm if text is substantial enough — avoid matching random short words
                                var htmlLen = (el.innerHTML || '').length;
                                if (htmlLen > 200) return true;
                            }
                        }
                    }
                    return false;
                }

                function isLegitimatePopup(el) {
                    if (!el || !el.querySelector) return false;

                    // Must have interactive elements (buttons/links — consent popups rarely use forms)
                    var hasInteractive = el.querySelector('button, a, input, select, textarea, [role="button"], [role="link"]');
                    if (!hasInteractive) return false;

                    // Must NOT have ad-related class/id signals (strong negative signal)
                    var cls = (el.className || '').toString().toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var adSignals = ['ad-', 'ad_', 'ads-', 'ads_', 'sponsor', 'promo',
                        'promotion', 'advertisement', 'ad-slot', 'ad-container',
                        'popup-ad', 'overlay-ad', 'floating-ad', 'sticky-ad'];
                    for (var j = 0; j < adSignals.length; j++) {
                        if (cls.indexOf(adSignals[j]) !== -1 || id.indexOf(adSignals[j]) !== -1) return false;
                    }

                    // Must have agreement/consent text content — scan FULL text
                    var text = (el.textContent || '').toLowerCase().trim();
                    var hasAgreeText = false;
                    if (text && text.length >= 20) {
                        var legitimacySignals = ['cookie', 'cookies', 'cookie policy', 'cookie preferences',
                            'privacy', 'privacy policy', 'data collection', 'data partners',
                            'third party', 'third-party', 'consent', 'preferences', 'manage preferences',
                            'agree', 'accept', 'terms', 'agreement',
                            'human verification', 'verify you', 'i am not a robot',
                            'perjanjian', 'kebijakan', 'privasi', 'kebijakan cookie',
                            'preferensi', 'verifikasi manusia', 'persyaratan',
                            'pusat preferensi', 'kelola preferensi'];
                        for (var i = 0; i < legitimacySignals.length; i++) {
                            if (text.indexOf(legitimacySignals[i]) !== -1) {
                                hasAgreeText = true;
                                break;
                            }
                        }
                    }
                    if (!hasAgreeText) return false;

                    return true;
                }

                function isLikelyAd(el) {
                    if (!el || !el.parentElement) return false;
                    if (el.nodeType !== 1) return false;
                    var tag = el.tagName ? el.tagName.toLowerCase() : '';
                    if (tag === 'html' || tag === 'body' || tag === 'head' || tag === 'title' || tag === 'meta' || tag === 'script' || tag === 'style' || tag === 'link') return false;

                    // ULTRA-GUARD: Direct consent text check
                    var consentTextSignals = ['cookie', 'cookies', 'cookie policy', 'cookie preferences',
                        'privacy policy', 'privacy', 'consent', 'preferences', 'manage preferences',
                        'data collection', 'data partners', 'third party',
                        'pusat preferensi', 'kelola preferensi', 'kebijakan cookie', 'kebijakan privasi',
                        'verifikasi manusia', 'persyaratan', 'perjanjian'];
                    var elText = (el.textContent || '').toLowerCase();
                    if (elText && elText.length >= 30) {
                        var consentMatches = 0;
                        for (var ct = 0; ct < consentTextSignals.length; ct++) {
                            if (elText.indexOf(consentTextSignals[ct]) !== -1) consentMatches++;
                        }
                        if (consentMatches >= 1 && el.querySelectorAll && el.querySelectorAll('button, a, [role="button"]').length >= 1) {
                            return false; // Has consent text + interactive elements — NOT an ad
                        }
                    }

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

                    // Skip elements that are part of a modal/dialog/navigation UI
                    // Modern web apps use overlays for in-page navigation
                    if (tag === 'dialog' || tag === 'nav' || tag === 'aside' || tag === 'header' || tag === 'footer') return false;
                    var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                    if (role === 'dialog' || role === 'navigation' || role === 'menu' || role === 'menubar' || role === 'tabpanel' || role === 'sidebar' || role === 'complementary') return false;
                    var ariaModal = (el.getAttribute && el.getAttribute('aria-modal') || '');
                    if (ariaModal === 'true' || ariaModal === 'false') return false;

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

                    // Skip if element has interactive/clickable children — likely a legitimate overlay
                    if (el.querySelector) {
                        var hasInteractive = el.querySelector('button, a, input, select, textarea, [role="button"], [role="tab"], [role="menuitem"], [onclick]');
                        if (hasInteractive) {
                            // Skip if it has more than 2 interactive elements — definitely a legit UI
                            var allInteractive = el.querySelectorAll('button, a, input, select, textarea, [role="button"], [role="tab"], [role="menuitem"]');
                            if (allInteractive.length >= 2) return false;
                            // Skip if the interactive element is a navigation link (<a href="...">)
                            if (hasInteractive.tagName === 'A' && hasInteractive.href && hasInteractive.href !== '#') return false;
                        }
                    }

                    // Re-check for ad class name but with strong safeguards
                    if (hasAdClassName(el)) {
                        var id = (el.id || '').toLowerCase();
                        if (id === 'content' || id === 'main' || id === 'article' || id === 'wrapper' || id === 'app' || id === 'root') return false;
                        // Only flag if parent is NOT a main content area
                        var pTag = el.parentElement ? el.parentElement.tagName.toLowerCase() : '';
                        var pClass = (el.parentElement ? el.parentElement.className || '' : '').toLowerCase();
                        if (pTag === 'main' || pTag === 'article' || pClass.indexOf('content') !== -1 || pClass.indexOf('main') !== -1) return false;
                        return true;
                    }

                    if (hasAdAttribute(el)) {
                        // Extra check: skip if element is inside a main content area
                        var pTag = el.parentElement ? el.parentElement.tagName.toLowerCase() : '';
                        var pClass = (el.parentElement ? el.parentElement.className || '' : '').toLowerCase();
                        if (pTag === 'main' || pTag === 'article' || pClass.indexOf('content') !== -1) return false;
                        return true;
                    }

                    if (hasAdUrl(el)) {
                        var parentClass = (el.className || '').toLowerCase();
                        var parentId = (el.id || '').toLowerCase();
                        if (parentClass.indexOf('content') !== -1 || parentId.indexOf('content') !== -1) return false;
                        return true;
                    }

                    // Small elements with ad text (likely a banner, not a modal)
                    if (rect.width < 600 && rect.height < 400) {
                        if (hasAdText(el)) return true;
                    }

                    return false;
                }

                function removeAd(el) {
                    try {
                        // Never hide popup/overlay elements protected by Phase 0
                        if (el.hasAttribute && el.hasAttribute('data-yue-popup')) return;

                        // ULTRA-GUARD: Direct consent text check — catch ANY element with consent text
                        // This works regardless of class/id naming
                        var consentTextSignals = ['cookie', 'cookies', 'cookie policy', 'cookie preferences',
                            'privacy policy', 'privacy', 'consent', 'preferences', 'manage preferences',
                            'data collection', 'data partners', 'third party',
                            'pusat preferensi', 'kelola preferensi', 'kebijakan cookie', 'kebijakan privasi',
                            'verifikasi manusia', 'persyaratan', 'perjanjian'];
                        var elText = (el.textContent || '').toLowerCase();
                        if (elText && elText.length >= 30) {
                            var consentMatches = 0;
                            for (var ct = 0; ct < consentTextSignals.length; ct++) {
                                if (elText.indexOf(consentTextSignals[ct]) !== -1) consentMatches++;
                            }
                            if (consentMatches >= 1 && el.querySelectorAll && el.querySelectorAll('button, a, [role="button"]').length >= 1) {
                                return; // Has consent text + buttons — definitely NOT an ad
                            }
                        }

                        // Never hide consent/agreement popups (check self first)
                        if (hasConsentSignals(el)) return;
                        if (isLegitimatePopup(el)) return;

                        // Never hide elements with semantic UI roles — modern web apps use these
                        var tag = el.tagName ? el.tagName.toLowerCase() : '';
                        if (tag === 'dialog' || tag === 'nav' || tag === 'aside' || tag === 'main') return;
                        var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                        if (role === 'dialog' || role === 'navigation' || role === 'menu' || role === 'menubar' || role === 'tabpanel' || role === 'sidebar' || role === 'complementary' || role === 'alertdialog') return;

                        // Check ancestors — 3 levels only (not to body)
                        var parent = el.parentElement;
                        var depth = 0;
                        while (parent && parent !== document.body && depth < 3) {
                            if (parent.hasAttribute && parent.hasAttribute('data-yue-popup')) return;
                            if (hasConsentSignals(parent)) return;
                            if (isLegitimatePopup(parent)) return;
                            // Also check parent text for consent signals
                            var pText = (parent.textContent || '').toLowerCase();
                            if (pText && pText.length >= 50) {
                                var pConsentMatches = 0;
                                for (var pt = 0; pt < consentTextSignals.length; pt++) {
                                    if (pText.indexOf(consentTextSignals[pt]) !== -1) pConsentMatches++;
                                }
                                if (pConsentMatches >= 1 && parent.querySelectorAll && parent.querySelectorAll('button, a').length >= 1) return;
                            }
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
                                // SAFETY: Never force display:block — turns inline <a> into block and breaks header/nav layout
                                // Only clear hiding we may have applied ourselves, or ensure visibility via non-layout props
                                el.style.removeProperty('display');
                                el.style.removeProperty('visibility');
                                el.style.removeProperty('opacity');
                                el.style.removeProperty('pointer-events');
                                // Ensure interactivity only if hidden — use low-priority setProperty
                                el.style.setProperty('visibility', 'visible');
                                el.style.setProperty('opacity', '1');
                                el.style.setProperty('pointer-events', 'auto');
                                el.disabled = false;
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
                                var src = (iframe.src || '').toLowerCase();
                                // Skip iframes that contain video players
                                if (iframe.querySelector && iframe.querySelector('video, source, canvas')) continue;
                                // Skip large iframes — likely video player or embedded content
                                if (rect.width > window.innerWidth * 0.6) continue;
                                if (rect.height > window.innerHeight * 0.6) continue;
                                // Only remove iframes with known ad URLs
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
                        // Only block VERY specific known ad timer patterns
                        // Do NOT intercept generic timers — breaks modern web apps
                        var adTimerPatterns = [
                            'popads', 'popcash', 'exoclick', 'adsterra',
                            'propellerads', 'mgid.com', 'juicyads',
                            'onclickads', 'popunder', 'popup-ad',
                        ];

                        window.setTimeout = function(fn, delay) {
                            if (delay > 0 && delay <= 5000 && typeof fn === 'function') {
                                var fnStr = fn.toString().toLowerCase();
                                // Only block if the function references known ad domains
                                for (var bi = 0; bi < adTimerPatterns.length; bi++) {
                                    if (fnStr.indexOf(adTimerPatterns[bi]) !== -1) {
                                        return origSetTimeout(function(){}, 86400000);
                                    }
                                }
                            }
                            return origSetTimeout.apply(this, arguments);
                        };

                        window.setInterval = function(fn, delay) {
                            if (typeof fn === 'function') {
                                var fnStr = fn.toString().toLowerCase();
                                for (var bi = 0; bi < adTimerPatterns.length; bi++) {
                                    if (fnStr.indexOf(adTimerPatterns[bi]) !== -1) {
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
                        // Only remove overflow:hidden if it was set by an ad-related element
                        // NEVER force overflow:visible on body/html — breaks modal navigation in modern web apps
                        var adOverflowEls = document.querySelectorAll('[style*="overflow: hidden"][class*="ad-"],[style*="overflow: hidden"][class*="ad_"],[style*="overflow: hidden"][class*="overlay-ad"],[style*="overflow: hidden"][id*="ad-"],[style*="overflow: hidden"][id*="ad_"]');
                        for (var oi = 0; oi < adOverflowEls.length; oi++) {
                            try {
                                adOverflowEls[oi].style.removeProperty('overflow');
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                function removeFixedOverlays() {
                    try {
                        if (window.__yue_in_fullscreen__) return;
                        // Only target elements with CLEAR ad-related class/id signals
                        // Skip any element that could be a legitimate modal/overlay/navigation
                        var adOverlaySelectors = [
                            'div[class*="ad-overlay"]', 'div[class*="overlay-ad"]',
                            'div[class*="popup-ad"]', 'div[class*="popunder"]',
                            'div[class*="interstitial"]', 'div[class*="exit-intent"]',
                            'div[id*="ad-overlay"]', 'div[id*="overlay-ad"]',
                            'div[class*="floating-ad"]', 'div[class*="sticky-ad"]',
                            'div[class*="bottom-banner"]', 'div[class*="top-banner"]',
                        ];
                        for (var si = 0; si < adOverlaySelectors.length; si++) {
                            try {
                                var els = document.querySelectorAll(adOverlaySelectors[si]);
                                for (var ei = 0; ei < els.length; ei++) {
                                    var el = els[ei];
                                    // Skip if it has interactive children (likely a legit UI component)
                                    if (el.querySelector && el.querySelector('button, a, input, select, textarea, [role="button"], nav, [role="navigation"]')) {
                                        // Check if it has form/input elements — skip entirely
                                        if (el.querySelector('input, select, textarea')) continue;
                                        // If it has buttons but no ad-related content, skip
                                        var text = (el.textContent || '').toLowerCase();
                                        if (text.indexOf('advertisement') === -1 && text.indexOf('sponsor') === -1 &&
                                            text.indexOf('iklan') === -1 && text.indexOf('promosi') === -1) continue;
                                    }
                                    removeAd(el);
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
                            // Debounce: only process if no new mutations for 500ms
                            if (window.__yueAdObserverTimer) clearTimeout(window.__yueAdObserverTimer);
                            window.__yueAdObserverTimer = setTimeout(function() {
                                var needsScan = false;
                                for (var i = 0; i < mutations.length; i++) {
                                    var added = mutations[i].addedNodes;
                                    for (var j = 0; j < added.length; j++) {
                                        var node = added[j];
                                        if (node.nodeType === 1) {
                                            // Only check elements with VERY clear ad signals
                                            if (node.className && typeof node.className === 'string') {
                                                var cn = node.className.toLowerCase();
                                                if (cn.indexOf('adsbygoogle') !== -1 || cn.indexOf('google_ads') !== -1) {
                                                    removeAd(node);
                                                    needsScan = true;
                                                }
                                            }
                                            // Check for iframes with ad URLs
                                            if (node.tagName === 'IFRAME' && hasAdUrl(node)) {
                                                removeAd(node);
                                                needsScan = true;
                                            }
                                        }
                                    }
                                }
                                if (needsScan) {
                                    forceEnableSkip();
                                }
                            }, 500);
                        } catch(e) {}
                    });
                    if (document.documentElement) {
                        window.__yueAdObserver.observe(document.documentElement, {
                            childList: true, subtree: true
                        });
                    }
                }

                if (!window.__yueRescanTimer) {
                    // Reduced scanning: only 2 passive rescans after initial
                    window.__yueRescanTimer = setInterval(function() {
                        try { scanAndRemove(); } catch(e) {}
                    }, 5000);
                    setTimeout(function() {
                        if (window.__yueRescanTimer) {
                            clearInterval(window.__yueRescanTimer);
                            window.__yueRescanTimer = null;
                        }
                    }, 30000);
                }

                // No more periodic overflow reset — was too aggressive for modern web apps
            } catch(e) {}
        })();
        """.trimIndent()
    }
}