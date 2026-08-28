package com.yue.browser.data.engine

object WebViewScripts {
    val fingerprintProtectionScript = """
        (function() {
            try {
                // 1. Hardware concurrency & device memory protection
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 4; }, configurable: true });
                Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; }, configurable: true });

                // 2. Battery API masking (commonly used to track sessions)
                if (navigator.getBattery) {
                    navigator.getBattery = function() {
                        return Promise.resolve({
                            charging: true,
                            chargingTime: 0,
                            dischargingTime: Infinity,
                            level: 1.0,
                            addEventListener: function() {},
                            removeEventListener: function() {},
                            dispatchEvent: function() { return true; },
                            onchargingchange: null,
                            onchargingtimechange: null,
                            ondischargingtimechange: null,
                            onlevelchange: null
                        });
                    };
                }

                // 3. UserAgentData masking
                if (navigator.userAgentData) {
                    var mockUserAgentData = {
                        brands: [
                            { brand: 'Chromium', version: '114' },
                            { brand: 'Not(A:Brand', version: '8' }
                        ],
                        mobile: true,
                        platform: 'Android'
                    };
                    Object.defineProperty(navigator, 'userAgentData', { get: function() { return mockUserAgentData; }, configurable: true });
                }

                // 4. Media devices spoofing
                if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                    navigator.mediaDevices.enumerateDevices = function() {
                        return Promise.resolve([
                            { deviceId: "default", kind: "audioinput", label: "Internal Microphone", groupId: "group1" },
                            { deviceId: "default", kind: "videoinput", label: "Front Camera", groupId: "group2" },
                            { deviceId: "default", kind: "audiooutput", label: "Default Speaker", groupId: "group3" }
                        ]);
                    };
                }
            } catch(e) {}
        })();
    """.trimIndent()

    val overlayAdRemoverScript = """
            (function() {
                if (window.yueOverlayRemoverInitialized) return;
                window.yueOverlayRemoverInitialized = true;

                // === AD NETWORK / POPUP DOMAIN BLOCKLIST ===
                var adDomains = [
                    'doubleclick.net', 'doubleclick.com', 'googlesyndication.com',
                    'googleadservices.com', 'google-analytics.com', 'googletagmanager.com',
                    'googletagservices.com', 'gstatic.com', 'googleusercontent.com',
                    'adnxs.com', 'adbrite.com', 'adsonar.com', 'advertisements.com',
                    'adblade.com', 'adform.net', 'adform.com', 'adtech.com',
                    'advalm.co', 'adsterra.com', 'adskeeper.co',
                    'popads.net', 'popcash.net', 'propellerads.com',
                    'richads.com', 'zeropark.com', 'taboola.com',
                    'outbrain.com', 'mgid.com', 'contentad.net',
                    'revcontent.com', 'zemanta.com', 'nativo.com',
                    'sharethrough.com', 'content.ad',
                    'cpmstar.com', 'popsunder.com', 'popsunder.net',
                    'sitemedia.com', 'medianet.com', 'smartadserver.com',
                    'valueclick.com', 'valueclickmedia.com',
                    'adengage.com', 'adtech.com', 'adeverywhere.com',
                    'vegas.com', 'vegasinsider.com',
                    'bonus.com', 'bonusbagar.com',
                    'bonus888.com', 'bonus138.com',
                    '388hero.com', 'dewa.com', 'dewacuan.com',
                    'gaza88.com', 'rusia777.com', 'maxwin.com',
                    'slotgacor.com', 'slotmaxwin.com', 'pentaslot.com',
                    'kaikoslot.com', 'agenjudionline.com',
                    'clickadilla.com', 'zeropark.net',
                    'exo.click', 'traffic.99hits.com',
                    'shrinkearn.com', 'shrinkme.io',
                    'linkvertise.com', 'work.ink',
                    'onshort.net', 'curiouscat.click',
                    'cuty.io', 'clk.sh', 'tii.gg',
                    'urlshortz.com', 'safelinkreview.com',
                    'clck.ru', 'clicky.me', 'clicky.gg',
                    'adsco.re', 'adsafelink.net',
                    'freehck.com', 'protectearn.com',
                    'prize.win', 'promo.xyz',
                    'dollarshaveclub.com', 'hellofresh.com',
                    'audible.com', 'gamefly.com',
                    'crashforgeeks.com', 'mmoexp.com',
                    'raid-haven.com', 'lootcakes.com',
                    'admybrand.com', 'admyrant.com',
                    'yandex.com/ad', 'yandex.ru/ad',
                    'outbound.net', 'outbrain.com',
                    'pulsepoint.com', 'pulsepoint.net',
                    'criteo.com', 'criteo.net',
                    'conversantmedia.com', 'conversantmedia.net',
                    'microsoft.com/ad', 'msn.com/ad',
                    'taboola.net', 'mgid.in',
                    'exo.com', 'exo.net',
                    'cdnsolutions.media',
                    'fegekejynewyhe.com',
                    'hevexiximyzywy.com',
                ];

                var adDomainPatterns = [
                    /doubleclick/, /googlead/, /googletag/, /adsense/,
                    /adnxs/, /adbrite/, /adform/, /adtech/,
                    /popads/, /popcash/, /propellerads/,
                    /richads/, /zeropark/, /taboola/, /outbrain/,
                    /mgid/, /contentad/, /revcontent/, /zemanta/,
                    /cpmstar/, /popsunder/, /sitemedia/, /medianet/,
                    /smartadserver/, /valueclick/,
                    /clickadilla/, /exo\.click/, /99hits/,
                    /shrinkearn/, /shrinkme/, /linkvertise/,
                    /work\.ink/, /onshort/, /curiouscat/,
                    /cuty\.io/, /clk\.sh/, /tii\.gg/,
                    /adsco\.re/, /adsafelink/, /freehck/,
                    /protectearn/, /prize\.win/, /promo\.xyz/,
                    /388hero/, /dewacuan/, /gaza88/,
                    /rusia777/, /maxwin/, /slotgacor/,
                    /slotmaxwin/, /pentaslot/, /kaikoslot/,
                    /clicky\.me/, /clicky\.gg/,
                    /bonus(?!.com)/, /gacor/, /judionline/,
                    /casino/, /slot-?machine/, /betting/,
                    /pokerstars?/, /bet365/, /1xbet/,
                    /fun88/, /pinnacle/, /williamhill/,
                    /gd388/, /idnslot/, /rtp/,
                    /pragmaticplay/, /habanero/,
                    /yggdrasil/, /microgaming/, /netent/,
                    /playtech/, /evolution/, /ezugi/,
                    /bbingo/, /big5/, /king85/,
                    /cambet/, /cmd368/, /tommy/,
                    /goreng/, /terbaru/, /paling/,
                    /deposit/, /withdraw/, /bonus/,
                    /promotion/, /welcome/, /sign\s*up/,
                    /no\s*deposit/, /free\s*spin/,
                    /bonus\s*code/, /promo\s*code/,
                    /cdnsolutions\./,
                    /fegekejynewyhe\./,
                ];

                function isAdUrl(url) {
                    if (!url) return false;
                    var lower = url.toLowerCase();
                    // Check against domain list
                    for (var i = 0; i < adDomains.length; i++) {
                        if (lower.indexOf(adDomains[i]) !== -1) return true;
                    }
                    // Check against patterns
                    for (var j = 0; j < adDomainPatterns.length; j++) {
                        if (adDomainPatterns[j].test(lower)) return true;
                    }
                    // Generic ad URL patterns
                    if (lower.indexOf('ad') !== -1 && (lower.indexOf('doubleclick') !== -1 || lower.indexOf('adservice') !== -1 || lower.indexOf('adserver') !== -1)) return true;
                    if (lower.indexOf('banner') !== -1 && lower.indexOf('.php') !== -1) return true;
                    if (lower.indexOf('popunder') !== -1 || lower.indexOf('popup') !== -1) return true;
                    return false;
                }

                function isAdDomain(hostname) {
                    if (!hostname) return false;
                    var lower = hostname.toLowerCase();
                    for (var i = 0; i < adDomains.length; i++) {
                        if (lower.indexOf(adDomains[i]) !== -1) return true;
                    }
                    for (var j = 0; j < adDomainPatterns.length; j++) {
                        if (adDomainPatterns[j].test(lower)) return true;
                    }
                    return false;
                }

                var currentHost = window.location.hostname;

                // === BLOCK window.open ===
                var originalOpen = window.open;
                window.open = function(url, name, specs, replace) {
                    if (!url || url === 'about:blank') {
                        return null;
                    }
                    try {
                        var targetUrl = new URL(url, window.location.href);
                        var targetHost = targetUrl.hostname;
                        if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                            if (isAdDomain(targetHost) || isAdUrl(url)) {
                                // Block the popup silently
                                document.dispatchEvent(new CustomEvent('yue-blocked-popup', { detail: { url: url } }));
                                return null;
                            }
                        }
                    } catch(e) {}
                    return originalOpen.apply(this, arguments);
                };

                // === BLOCK popunders (location assignment) ===
                // Hook location setter to catch popunders
                try {
                    var locationProto = Object.getPrototypeOf(window.location);
                    var origAssign = locationProto.assign;
                    var origReplace = locationProto.replace;
                    locationProto.assign = function(url) {
                        try {
                            var targetHost = '';
                            try { targetHost = new URL(url, window.location.href).hostname; } catch(e) {}
                            if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                                if (isAdDomain(targetHost) || isAdUrl(url)) {
                                    // Allow popunder — the ad will load in current tab
                                    // but we'll intercept it in shouldOverrideUrlLoading
                                }
                            }
                        } catch(e) {}
                        return origAssign.apply(this, arguments);
                    };
                    locationProto.replace = function(url) {
                        try {
                            var targetHost = '';
                            try { targetHost = new URL(url, window.location.href).hostname; } catch(e) {}
                            if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                                if (isAdDomain(targetHost) || isAdUrl(url)) {
                                    // Similar to assign
                                }
                            }
                        } catch(e) {}
                        return origReplace.apply(this, arguments);
                    };
                } catch(e) {}

                // === BLOCK ad iframes & video ads ===
                function blockAdIframes() {
                    try {
                        // Find all iframes
                        var iframes = document.querySelectorAll('iframe');
                        for (var i = 0; i < iframes.length; i++) {
                            var iframe = iframes[i];
                            var src = (iframe.src || '').toLowerCase();
                            // Block iframes loading ad networks
                            if (isAdUrl(src) || isAdDomain(src)) {
                                iframe.style.setProperty('display', 'none', 'important');
                                iframe.style.setProperty('visibility', 'hidden', 'important');
                                iframe.removeAttribute('src');
                                continue;
                            }
                            // Block iframes with video ad patterns
                            var adPatterns = ['vast', 'vpaid', 'videoad', 'videoad', 'preroll', 'midroll', 'instream', 'outstream', 'pre-roll', 'mid-roll', 'adbreak', 'ad-unit', 'video-ads'];
                            for (var ap = 0; ap < adPatterns.length; ap++) {
                                if (src.indexOf(adPatterns[ap]) !== -1) {
                                    iframe.style.setProperty('display', 'none', 'important');
                                    iframe.style.setProperty('visibility', 'hidden', 'important');
                                    iframe.removeAttribute('src');
                                    break;
                                }
                            }
                        }
                        // Block video elements with ad sources
                        var videos = document.querySelectorAll('video');
                        for (var v = 0; v < videos.length; v++) {
                            var video = videos[v];
                            var src2 = (video.currentSrc || video.src || '').toLowerCase();
                            if (src2 && isAdUrl(src2)) {
                                video.style.setProperty('display', 'none', 'important');
                                video.style.setProperty('visibility', 'hidden', 'important');
                            }
                            // Check source children
                            var sources = video.querySelectorAll('source');
                            for (var s = 0; s < sources.length; s++) {
                                var srcSrc = (sources[s].src || '').toLowerCase();
                                if (srcSrc && isAdUrl(srcSrc)) {
                                    video.style.setProperty('display', 'none', 'important');
                                    video.style.setProperty('visibility', 'hidden', 'important');
                                    break;
                                }
                            }
                        }
                    } catch(e) {}
                }
                blockAdIframes();

                // Run periodically to catch dynamically loaded iframes
                if (!window.__yueAdIframeInterval) {
                    window.__yueAdIframeInterval = setInterval(function() {
                        try { blockAdIframes(); } catch(e) {}
                    }, 1000);
                    // Stop after 30 seconds
                    setTimeout(function() {
                        if (window.__yueAdIframeInterval) {
                            clearInterval(window.__yueAdIframeInterval);
                            window.__yueAdIframeInterval = null;
                        }
                    }, 30000);
                }

                // === BLOCK video ad players ===
                function blockVideoAdPlayers() {
                    try {
                        // Block known video ad player classes
                        var playerSelectors = [
                            '[class*="ad-player"]', '[class*="adplayer"]',
                            '[class*="video-ad"]', '[class*="videoad"]',
                            '[class*="preroll"]', '[class*="pre-roll"]',
                            '[class*="midroll"]', '[class*="mid-roll"]',
                            '[class*="instream"]', '[class*="outstream"]',
                            '[class*="vast-player"]', '[class*="vpaid"]',
                            '[class*="ad-break"]', '[class*="adbreak"]',
                            '[class*="advertisement"]', '[class*="advert"]',
                            '[class*="ad-slot"]', '[class*="adslot"]',
                            '[id*="ad-player"]', '[id*="adplayer"]',
                            '[id*="video-ad"]', '[id*="videoad"]',
                            '[id*="preroll"]', '[id*="midroll"]',
                            '[data-ad-slot]', '[data-ad-client]',
                            '[data-google-query-id]',
                            '[data-gpt-ad-unit-path]',
                            'ins-ad-player', 'ins_ad_player',
                            '.adsbygoogle', '.ad-slot',
                        ].join(',');
                        var adPlayers = document.querySelectorAll(playerSelectors);
                        for (var p = 0; p < adPlayers.length; p++) {
                            var player = adPlayers[p];
                            var pRect = player.getBoundingClientRect();
                            if (pRect.width > 0 && pRect.height > 0) {
                                // Check if it's a legitimate video player (has controls)
                                var hasControls = player.querySelector && player.querySelector('video[controls], .video-controls, [class*="play-btn"]');
                                // Check if it has ad signals
                                var pCls = (player.className || '').toString().toLowerCase();
                                var pId = (player.id || '').toLowerCase();
                                var isAdPlayer = pCls.indexOf('ad') !== -1 || pId.indexOf('ad') !== -1 ||
                                    pCls.indexOf('preroll') !== -1 || pCls.indexOf('midroll') !== -1 ||
                                    pCls.indexOf('instream') !== -1 || pCls.indexOf('outstream') !== -1 ||
                                    pCls.indexOf('advertisement') !== -1 || pCls.indexOf('adbreak') !== -1;
                                if (isAdPlayer && !hasControls) {
                                    player.style.setProperty('display', 'none', 'important');
                                    player.style.setProperty('visibility', 'hidden', 'important');
                                }
                            }
                        }
                    } catch(e) {}
                }
                blockVideoAdPlayers();

                if (!window.__yueAdPlayerInterval) {
                    window.__yueAdPlayerInterval = setInterval(function() {
                        try { blockVideoAdPlayers(); } catch(e) {}
                    }, 1500);
                    setTimeout(function() {
                        if (window.__yueAdPlayerInterval) {
                            clearInterval(window.__yueAdPlayerInterval);
                            window.__yueAdPlayerInterval = null;
                        }
                    }, 30000);
                }

                // Also intercept <a target="_blank"> links that go to ad domains
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var anchor = target && target.closest ? target.closest('a') : null;
                    if (anchor) {
                        var href = anchor.getAttribute('href');
                        if (href && href.indexOf('javascript:') !== 0 && href.indexOf('#') !== 0) {
                            try {
                                var tgtUrl = new URL(href, window.location.href);
                                var tgtHost = tgtUrl.hostname;
                                if (tgtHost && tgtHost !== currentHost && !tgtHost.endsWith('.' + currentHost)) {
                                    if (isAdDomain(tgtHost) || isAdUrl(href)) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        return;
                                    }
                                }
                            } catch(err) {}
                        }
                    }
                }, true);


                // Helper: check if element is a consent/agreement popup
                function isConsentPopup(el) {
                    if (!el) return false;
                    var cls = (el.className || '').toString().toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var role = (el.getAttribute && el.getAttribute('role') || '').toLowerCase();
                    if (role === 'dialog') return true;
                    var signals = ['consent', 'agreement', 'terms', 'privacy', 'cookie-banner', 'cookie-consent',
                        'gdpr', 'ccpa', 'optanon', 'onetrust', 'cookiebot', 'trustarc', 'klaro',
                        'cookie-notice', 'CookieNotice', 'CookieBanner'];
                    for (var s = 0; s < signals.length; s++) {
                        if (cls.indexOf(signals[s]) !== -1 || id.indexOf(signals[s]) !== -1) return true;
                    }
                    return false;
                }

                // Helper: check if element is a legitimate popup (forms, buttons, agreement text)
                function isLegitimatePopup(el) {
                    if (!el || !el.querySelector) return false;
                    // Must be a LARGE overlay (not small inline ad)
                    var rect;
                    try { rect = el.getBoundingClientRect(); } catch(e) { return false; }
                    if (rect.width < window.innerWidth * 0.3 && rect.height < window.innerHeight * 0.3) return false;
                    // Must be positioned as overlay
                    var style = window.getComputedStyle(el);
                    if (style.position !== 'fixed' && style.position !== 'absolute') return false;
                    // Must have form/input elements
                    var hasForm = el.querySelector('form, input, select, textarea, [type="text"], [type="email"], [type="password"]');
                    if (!hasForm) return false;
                    // Must have agreement/consent text
                    var text = (el.textContent || '').toLowerCase().trim().substring(0, 500);
                    var legitSignals = ['agree', 'accept', 'terms', 'agreement',
                        'privacy policy', 'cookie policy', 'consent',
                        'human verification', 'verify you', 'i am not a robot'];
                    var hasAgreeText = false;
                    for (var i = 0; i < legitSignals.length; i++) {
                        if (text.indexOf(legitSignals[i]) !== -1) { hasAgreeText = true; break; }
                    }
                    if (!hasAgreeText) return false;
                    // Must NOT have ad signals
                    var cls = (el.className || '').toString().toLowerCase();
                    var id = (el.id || '').toLowerCase();
                    var adSignals = ['ad-', 'ad_', 'ads-', 'sponsor', 'promo',
                        'promotion', 'advertisement', 'ad-slot', 'ad-container'];
                    for (var j = 0; j < adSignals.length; j++) {
                        if (cls.indexOf(adSignals[j]) !== -1 || id.indexOf(adSignals[j]) !== -1) return false;
                    }
                    return true;
                }

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var anchor = target && target.closest ? target.closest('a') : null;
                    if (anchor) {
                        var href = anchor.getAttribute('href');
                        if (href && href.indexOf('javascript:') !== 0 && href.indexOf('#') !== 0) {
                            try {
                                var currentHost = window.location.hostname;
                                var targetUrl = new URL(href, window.location.href);
                                var targetHost = targetUrl.hostname;
                                if (targetHost && targetHost !== currentHost && targetHost.indexOf('.' + currentHost) !== (targetHost.length - ('.' + currentHost).length)) {
                                    var rect = anchor.getBoundingClientRect();
                                    var viewWidth = window.innerWidth || document.documentElement.clientWidth;
                                    var viewHeight = window.innerHeight || document.documentElement.clientHeight;
                                    var coversLargeArea = (rect.width * rect.height) > (viewWidth * viewHeight * 0.25);
                                    // Don't block consent/agreement links
                                    if (coversLargeArea && !isConsentPopup(anchor)) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        e.stopImmediatePropagation();
                                    }
                                }
                            } catch(err) {}
                        }
                    }

                    // Only block overlays on click if they are clearly ad-related
                    // Do NOT block legitimate popups/agreements/menus
                    var current = target;
                    var depth = 0;
                    var textTags = /^(SPAN|P|B|I|STRONG|EM|H[1-6]|FONT|CODE|SUB|SUP)$/i;
                    while (current && current !== document.body && current !== document.documentElement && depth < 5) {
                        if (current.__yueSafe__) {
                            current = current.parentElement;
                            depth++;
                            continue;
                        }
                        // Skip popup/overlay elements protected by Yue
                        if (current.hasAttribute && current.hasAttribute('data-yue-popup')) {
                            current.__yueSafe__ = true;
                            current = current.parentElement;
                            depth++;
                            continue;
                        }
                        if (textTags.test(current.tagName)) {
                            current.__yueSafe__ = true;
                            current = current.parentElement;
                            depth++;
                            continue;
                        }
                        try {
                            // Never block consent/agreement popups
                            if (isConsentPopup(current)) {
                                current.__yueSafe__ = true;
                                current = current.parentElement;
                                depth++;
                                continue;
                            }
                            // Never block legitimate popups (forms, agreement text)
                            if (isLegitimatePopup(current)) {
                                current.__yueSafe__ = true;
                                current = current.parentElement;
                                depth++;
                                continue;
                            }
                            // Check if this element has clickable children (buttons, inputs, etc.)
                            var hasClickableChildren = false;
                            if (current.querySelectorAll) {
                                var clickables = current.querySelectorAll('button, a, input, [role="button"], select, textarea, label');
                                if (clickables.length > 0) hasClickableChildren = true;
                            }
                            if (hasClickableChildren) {
                                current.__yueSafe__ = true;
                                current = current.parentElement;
                                depth++;
                                continue;
                            }

                            var style = window.getComputedStyle(current);
                            if (style && (style.position === 'fixed' || style.position === 'absolute')) {
                                var rect = current.getBoundingClientRect();
                                var viewWidth = window.innerWidth || document.documentElement.clientWidth;
                                var viewHeight = window.innerHeight || document.documentElement.clientHeight;
                                var coversLargeArea = (rect.width * rect.height) > (viewWidth * viewHeight * 0.5);
                                if (coversLargeArea) {
                                    var isAdOverlay = false;
                                    var className = (current.className || '').toString().toLowerCase();
                                    var idName = (current.id || '').toString().toLowerCase();
                                    var zIndex = parseInt(style.zIndex) || 0;
                                    var opacity = parseFloat(style.opacity);
                                    
                                    // Only block if:
                                    // 1) Very high z-index AND ad-related class/id, OR
                                    // 2) Very high z-index AND transparent (invisible overlay trap)
                                    if (zIndex > 999 && (className.indexOf('ad-') !== -1 || className.indexOf('_ad') !== -1 || idName.indexOf('ad-') !== -1 || idName.indexOf('_ad') !== -1)) {
                                        isAdOverlay = true;
                                    } else if (zIndex > 999 && (isNaN(opacity) || opacity < 0.1 || style.backgroundColor === 'transparent' || style.backgroundColor.indexOf('rgba(0,0,0,0') !== -1)) {
                                        isAdOverlay = true;
                                    }

                                    if (isAdOverlay) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        e.stopImmediatePropagation();
                                        try {
                                            // Use minimal hiding — never remove DOM
                                            current.style.setProperty('display', 'none', 'important');
                                            current.style.setProperty('visibility', 'hidden', 'important');
                                            current.style.setProperty('opacity', '0', 'important');
                                            // Do NOT set pointer-events:none, do NOT removeChild
                                        } catch(e) {}
                                        return;
                                    }
                                }
                            }
                            current.__yueSafe__ = true;
                        } catch(err) {}
                        current = current.parentElement;
                        depth++;
                    }
                }, true);

                (function() {
                    var lastScrollTop = 0;
                    var threshold = 25;
                    document.addEventListener('scroll', function(e) {
                        var scrollTop = 0;
                        var tgt = e.target;
                        if (!tgt) return;
                        if (tgt === document || tgt === window || tgt === document.documentElement || tgt === document.body) {
                            scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
                        } else if (tgt.scrollTop !== undefined) {
                            scrollTop = tgt.scrollTop;
                        } else {
                            return;
                        }
                        if (scrollTop === 0) {
                            if (window.YueScroll && window.YueScroll.onScrollChanged) window.YueScroll.onScrollChanged(true);
                            lastScrollTop = 0;
                            return;
                        }
                        var diff = scrollTop - lastScrollTop;
                        if (Math.abs(diff) > threshold) {
                            if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                window.YueScroll.onScrollChanged(diff > 0 && scrollTop > 50 ? false : true);
                            }
                            lastScrollTop = scrollTop;
                        }
                    }, true);
                })();

                function killAntiAdblock() {
                    try {
                        if (window.__yue_in_fullscreen__) return;
                        if (document.body) {
                            if (document.body.style.overflow === 'hidden') document.body.style.overflow = 'auto';
                            document.body.style.setProperty('overflow', 'auto', 'important');
                            document.body.style.setProperty('position', '', 'important');
                            var bodyPos = window.getComputedStyle(document.body).position;
                            if (bodyPos === 'fixed') {
                                document.body.style.setProperty('position', 'relative', 'important');
                            }
                        }
                        if (document.documentElement) {
                            if (document.documentElement.style.overflow === 'hidden') document.documentElement.style.overflow = 'auto';
                            document.documentElement.style.setProperty('overflow', 'auto', 'important');
                        }
                    } catch (e) {}
                }

                // Find and remove elements that are blocking the page (full-screen overlays)
                function removeBlockingOverlays() {
                    try {
                        if (window.__yue_in_fullscreen__) return;
                        var all = document.querySelectorAll('div, section, aside, span');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            try {
                                // Skip popup/overlay elements protected by Yue
                                if (el.hasAttribute && el.hasAttribute('data-yue-popup')) continue;
                                // Skip consent/agreement popups
                                if (isConsentPopup(el)) continue;
                                // Skip legitimate popups (forms, buttons, agreement text)
                                if (isLegitimatePopup(el)) continue;
                                // Skip elements with clickable children (buttons, forms)
                                if (el.querySelectorAll) {
                                    var kids = el.querySelectorAll('button, a, input, [role="button"]');
                                    if (kids.length > 0) continue;
                                }
                                var style = window.getComputedStyle(el);
                                if (style.position === 'fixed' || style.position === 'absolute') {
                                    var zIdx = parseInt(style.zIndex) || 0;
                                    if (zIdx > 500) {
                                        var rect = el.getBoundingClientRect();
                                        if (rect.width >= window.innerWidth * 0.8 && rect.height >= window.innerHeight * 0.8) {
                                            // Skip if contains video player
                                            if (el.querySelector && el.querySelector('video, iframe[src*="video"], iframe[src*="player"]')) continue;
                                            // Only hide if it has ad-related class/id
                                            var cls = (el.className || '').toString().toLowerCase();
                                            var id = (el.id || '').toLowerCase();
                                            var hasAdSignals = cls.indexOf('ad-') !== -1 || cls.indexOf('ad_') !== -1 ||
                                                cls.indexOf('sponsor') !== -1 || cls.indexOf('promo') !== -1 ||
                                                cls.indexOf('popup-ad') !== -1 || cls.indexOf('overlay-ad') !== -1 ||
                                                id.indexOf('ad-') !== -1 || id.indexOf('ad_') !== -1;
                                            if (hasAdSignals) {
                                                el.style.setProperty('display', 'none', 'important');
                                                el.style.setProperty('visibility', 'hidden', 'important');
                                                el.style.setProperty('opacity', '0', 'important');
                                                // Do NOT set pointer-events:none
                                            }
                                        }
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                killAntiAdblock();
                removeBlockingOverlays();

                // Run killAntiAdblock and overlay removal less frequently to avoid flicker
                setInterval(function() { try { killAntiAdblock(); } catch(e) {} }, 2000);
                setInterval(function() { try { removeBlockingOverlays(); } catch(e) {} }, 5000);
            })();
        """.trimIndent()

    fun getExtensionStoreInstallerScript(enabledAddonsJson: String): String {
        return """
            (function() {
                var enabledAddons = $enabledAddonsJson;
                var currentUrl = window.location.href;
                var pageAddonId = null;
                if (currentUrl.includes("ublock") || currentUrl.includes("cjpalhdlnbpafiamejdnhcphjbkeiagm")) {
                    pageAddonId = "ublock";
                } else if (currentUrl.includes("darkreader") || currentUrl.includes("eimadpcaloflhjddepbbgoikcjaggafg")) {
                    pageAddonId = "darkreader";
                } else if (currentUrl.includes("translator") || currentUrl.includes("mchibihcapipjolgdaiegimacnlaaldg")) {
                    pageAddonId = "translator";
                }

                var style = document.createElement('style');
                style.textContent = `
                    .e-f-u-Md, button:disabled, [role="button"]:disabled, .g-Nd-Hf-v, [aria-disabled="true"], [disabled] { 
                        pointer-events: auto !important; 
                        cursor: pointer !important; 
                        opacity: 1 !important; 
                        background-color: #0b57d0 !important;
                        color: #ffffff !important;
                    }
                `;
                document.head.appendChild(style);

                function setupInstallHook() {
                    var isAlreadyInstalled = pageAddonId && enabledAddons.indexOf(pageAddonId) !== -1;
                    var buttons = document.querySelectorAll('button, [role="button"], a');
                    buttons.forEach(function(btn) {
                        var text = (btn.textContent || '').trim().toLowerCase();
                        var isInstallButton = text === 'dapatkan' || 
                                              text.includes('add to chrome') || 
                                              text.includes('tambahkan ke chrome') || 
                                              text === 'get' || 
                                              text.includes('add to firefox') || 
                                              text.includes('tambahkan ke firefox') || 
                                              text.includes('download file') || 
                                              text.includes('download the new firefox') ||
                                              text.includes('download firefox') ||
                                              btn.classList.contains('AMInstallButton');
                        
                        if (isInstallButton) {
                            if (isAlreadyInstalled) {
                                btn.textContent = '✓ Terpasang (Yue Browser)';
                                btn.style.backgroundColor = '#1e8e3e';
                                btn.style.color = '#ffffff';
                                btn.style.cursor = 'default';
                                btn.disabled = true;
                                btn.setAttribute('disabled', 'true');
                                btn.style.pointerEvents = 'none';
                            } else {
                                if (btn.disabled) btn.disabled = false;
                                btn.removeAttribute('aria-disabled');
                                btn.removeAttribute('disabled');
                                if (btn.style) {
                                    btn.style.pointerEvents = 'auto';
                                    btn.style.opacity = '1';
                                }
                                if (!btn.dataset.yueHooked) {
                                    btn.dataset.yueHooked = 'true';
                                    btn.addEventListener('click', function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        if (window.YueAddons) {
                                            window.YueAddons.installAddon(window.location.href);
                                            // Immediately turn to green "Terpasang"
                                            btn.textContent = '✓ Terpasang (Yue Browser)';
                                            btn.style.backgroundColor = '#1e8e3e';
                                            btn.style.color = '#ffffff';
                                            btn.style.cursor = 'default';
                                            btn.disabled = true;
                                            btn.setAttribute('disabled', 'true');
                                            btn.style.pointerEvents = 'none';
                                            enabledAddons.push(pageAddonId);
                                        }
                                    }, true);
                                }
                            }
                        }
                    });
                }
                setupInstallHook();
                setInterval(setupInstallHook, 1000);
            })();
        """.trimIndent()
    }

    val wechatOverrideScript = """
        (function() {
            try { Object.defineProperty(window, 'YueAddons', { value: undefined, writable: false, configurable: true }); } catch(e) {}
            try { delete window.YueAddons; } catch(e) {}
            try { Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'plugins', { get: function() { return [1,2,3,4,5]; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'languages', { get: function() { return ['zh-CN','zh','en']; } }); } catch(e) {}
            if (!window.chrome) {
                try { window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} }; } catch(e) {}
            }
            try { Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'vendorSub', { get: function() { return ''; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'productSub', { get: function() { return '20030107'; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'product', { get: function() { return 'Gecko'; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'appVersion', { get: function() { return navigator.userAgent; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'appName', { get: function() { return 'Netscape'; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'appCodeName', { get: function() { return 'Mozilla'; } }); } catch(e) {}
            try { Object.defineProperty(navigator, 'doNotTrack', { get: function() { return '1'; } }); } catch(e) {}
            if (navigator.userAgentData) {
                try { Object.defineProperty(navigator.userAgentData, 'mobile', { get: function() { return true; } }); } catch(e) {}
                try { Object.defineProperty(navigator.userAgentData, 'platform', { get: function() { return 'Android'; } }); } catch(e) {}
            }
            try { document.documentElement.setAttribute('data-useragent', navigator.userAgent); } catch(e) {}
        })();
    """.trimIndent()

    private fun escapeJsString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun getPlatformOverrideScript(platformStr: String, expectedUA: String, isMobileVal: Boolean, platformUAData: String): String {
        val safePlatform = escapeJsString(platformStr)
        val safeUA = escapeJsString(expectedUA)
        val safeUAData = escapeJsString(platformUAData)
        return """
            (function() {
                try { Object.defineProperty(navigator, 'webdriver', { get: function() { return false; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'plugins', {
                    get: function() {
                        var p = [1,2,3,4,5];
                        p.item = function(i) { return this[i]; };
                        p.namedItem = function(n) { return null; };
                        return p;
                    }
                }); } catch(e) {}
                try { Object.defineProperty(navigator, 'languages', { get: function() { return ['id-ID', 'id', 'en-US', 'en']; } }); } catch(e) {}
                if (!window.chrome) {
                    try { window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){} }; } catch(e) {}
                }
                try { Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 5; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'vendor', { get: function() { return 'Google Inc.'; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'platform', { get: function() { return '$safePlatform'; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'userAgent', { get: function() { return '$safeUA'; } }); } catch(e) {}
                if (navigator.userAgentData) {
                    try { Object.defineProperty(navigator.userAgentData, 'mobile', { get: function() { return $isMobileVal; } }); } catch(e) {}
                    try { Object.defineProperty(navigator.userAgentData, 'platform', { get: function() { return '$safeUAData'; } }); } catch(e) {}
                }
            })();
        """.trimIndent()
    }

    val stateListenerScript = """
            (function() {
                try {
                    if (window.__yue_state_hooked__) return;
                    window.__yue_state_hooked__ = true;

                    // Track SPA pushState depth manually
                    var spaDepth = 0;

                    function notifyState() {
                        try {
                            if (window.YueState && window.YueState.onStateChanged) {
                                window.YueState.onStateChanged();
                            }
                        } catch(e) {}
                    }

                    function notifySpaDepth() {
                        try {
                            if (window.YueState && window.YueState.onSpaDepthChanged) {
                                window.YueState.onSpaDepthChanged(spaDepth);
                            }
                        } catch(e) {}
                    }

                    // Listen to standard popstate (back/forward in SPA) and hashchange
                    window.addEventListener('popstate', function() {
                        spaDepth = Math.max(0, spaDepth - 1);
                        notifyState();
                        notifySpaDepth();
                    });

                    window.addEventListener('hashchange', function() {
                        notifyState();
                        notifySpaDepth();
                    });

                    // Intercept pushState and replaceState to catch dynamic router changes
                    var origPush = window.history.pushState;
                    if (origPush) {
                        window.history.pushState = function() {
                            var context = (this instanceof History) ? this : window.history;
                            var res = origPush.apply(context, arguments);
                            spaDepth++;
                            notifyState();
                            notifySpaDepth();
                            return res;
                        };
                    }
                    var origReplace = window.history.replaceState;
                    if (origReplace) {
                        window.history.replaceState = function() {
                            var context = (this instanceof History) ? this : window.history;
                            var res = origReplace.apply(context, arguments);
                            notifyState();
                            notifySpaDepth();
                            return res;
                        };
                    }

                    // Also notify when page clicks occur (extra safety)
                    document.addEventListener('click', function() {
                        setTimeout(notifyState, 150);
                    });
                } catch(e) {}
            })();
        """.trimIndent()

    val eventListenerHookScript = """
            (function() {
                try {
                    if (window.__yue_event_listener_hooked__) return;
                    window.__yue_event_listener_hooked__ = true;

                    var originalAdd = EventTarget.prototype.addEventListener;
                    var originalRemove = EventTarget.prototype.removeEventListener;

                    // Capture BOUND references for document and window BEFORE hooking.
                    // In Android WebView, EventTarget.prototype.addEventListener.call(document, ...)
                    // silently fails — the listener never fires. Using bound references avoids this.
                    var boundDocAdd = document.addEventListener.bind(document);
                    var boundDocRemove = document.removeEventListener.bind(document);
                    var boundWinAdd = window.addEventListener.bind(window);
                    var boundWinRemove = window.removeEventListener.bind(window);

                    // Expose native references IMMEDIATELY after capture,
                    // BEFORE any prototype modification that might throw.
                    window.__yue_native_addEventListener__ = originalAdd;
                    window.__yue_native_removeEventListener__ = originalRemove;
                    window.__yue_native_doc_add__ = boundDocAdd;
                    window.__yue_native_doc_remove__ = boundDocRemove;
                    window.__yue_native_win_add__ = boundWinAdd;
                    window.__yue_native_win_remove__ = boundWinRemove;

                    var hookedAdd = function(type, listener, options) {
                        var useCapture = false;
                        if (typeof options === 'boolean') useCapture = options;
                        else if (options && typeof options === 'object') useCapture = !!options.capture;

                        var isPickerReg = false;
                        if (listener) {
                            if (listener.__yue_picker_handler__) isPickerReg = true;
                            else if (listener.handleEvent && listener.handleEvent.__yue_picker_handler__) isPickerReg = true;
                            else if (window.__yue_picker_handlers__ && window.__yue_picker_handlers__.indexOf(listener) !== -1) isPickerReg = true;
                        }
                        if (type.indexOf('click') !== -1 || type.indexOf('touch') !== -1) {
                            console.log('YueHook: addEventListener type=' + type + ' isPicker=' + isPickerReg);
                        }

                        var wrappedListener = function(event) {
                            if (window.__yuePickerActive__) {
                                // Only block non-picker events when picker overlay is actually visible
                                // Check if picker overlay is in the DOM and visible
                                var pickerOverlay = document.getElementById('__yue_picker_overlay__');
                                var pickerVisible = false;
                                if (pickerOverlay) {
                                    var po = window.getComputedStyle(pickerOverlay);
                                    pickerVisible = po.display !== 'none' && po.visibility !== 'hidden';
                                }
                                if (!pickerVisible) {
                                    // Picker flag is set but overlay not visible - this is a stale state.
                                    // Don't block events, just pass through normally.
                                    window.__yuePickerActive__ = false;
                                } else {
                                    var isPicker = false;
                                    if (listener) {
                                        if (listener.__yue_picker_handler__) isPicker = true;
                                        else if (listener.handleEvent && listener.handleEvent.__yue_picker_handler__) isPicker = true;
                                        else if (window.__yue_picker_handlers__ && window.__yue_picker_handlers__.indexOf(listener) !== -1) isPicker = true;
                                    }
                                    if (type.indexOf('click') !== -1 || type.indexOf('touch') !== -1) {
                                        console.log('YueHook: event=' + (event ? event.type : 'null') + ' target=' + (event && event.target ? event.target.id || event.target.tagName : 'null') + ' isPicker=' + isPicker);
                                    }
                                    if (isPicker) {
                                        try {
                                            if (typeof listener === 'function') {
                                                return listener.apply(this, arguments);
                                            } else if (listener.handleEvent) {
                                                return listener.handleEvent.apply(listener, arguments);
                                            }
                                        } catch(e) {}
                                        return;
                                    }
                                    if (type.indexOf('click') !== -1 || type.indexOf('touch') !== -1 || type.indexOf('mouse') !== -1 || type.indexOf('pointer') !== -1 || type === 'contextmenu') {
                                        return;
                                    }
                                }
                            }
                            try {
                                if (typeof listener === 'function') {
                                    return listener.apply(this, arguments);
                                } else if (listener && typeof listener.handleEvent === 'function') {
                                    return listener.handleEvent.apply(listener, arguments);
                                }
                            } catch(e) {
                                // Don't crash page scripts
                            }
                        };

                        if (!this.__yue_listeners__) this.__yue_listeners__ = [];
                        this.__yue_listeners__.push({
                            type: type,
                            listener: listener,
                            wrapped: wrappedListener,
                            useCapture: useCapture
                        });

                        // Use bound references for document/window to avoid .call() bug
                        if (this === document) {
                            return boundDocAdd(type, wrappedListener, options);
                        } else if (this === window) {
                            return boundWinAdd(type, wrappedListener, options);
                        }
                        return originalAdd.call(this, type, wrappedListener, options);
                    };

                    var hookedRemove = function(type, listener, options) {
                        var useCapture = false;
                        if (typeof options === 'boolean') useCapture = options;
                        else if (options && typeof options === 'object') useCapture = !!options.capture;

                        if (this.__yue_listeners__) {
                            var index = this.__yue_listeners__.findIndex(function(item) {
                                return item.type === type && item.listener === listener && item.useCapture === useCapture;
                            });
                            if (index !== -1) {
                                var wrapped = this.__yue_listeners__[index].wrapped;
                                this.__yue_listeners__.splice(index, 1);
                                if (this === document) {
                                    return boundDocRemove(type, wrapped, options);
                                } else if (this === window) {
                                    return boundWinRemove(type, wrapped, options);
                                }
                                return originalRemove.call(this, type, wrapped, options);
                            }
                        }
                        if (this === document) {
                            return boundDocRemove(type, listener, options);
                        } else if (this === window) {
                            return boundWinRemove(type, listener, options);
                        }
                        return originalRemove.call(this, type, listener, options);
                    };

                    EventTarget.prototype.addEventListener = hookedAdd;
                    EventTarget.prototype.removeEventListener = hookedRemove;

                    if (window.Window && Window.prototype.addEventListener !== hookedAdd) {
                        Window.prototype.addEventListener = hookedAdd;
                    }
                    if (window.Window && Window.prototype.removeEventListener !== hookedRemove) {
                        Window.prototype.removeEventListener = hookedRemove;
                    }
                    if (window.Document && Document.prototype.addEventListener !== hookedAdd) {
                        Document.prototype.addEventListener = hookedAdd;
                    }
                    if (window.Document && Document.prototype.removeEventListener !== hookedRemove) {
                        Document.prototype.removeEventListener = hookedRemove;
                    }

                } catch(e) {}
            })();
        """.trimIndent()

    val mediaSessionScript = """
            (function() {
                try {
                    // Hide YouTube's native speed overlays (always check/inject with shadow DOM pierce and attachShadow override)
                    try {
                        var injectStyle = function(root) {
                            if (!root) return;
                            var styleId = 'yue-hide-native-speed-overlay';
                            if (!root.getElementById(styleId)) {
                                var style = document.createElement('style');
                                style.id = styleId;
                                style.textContent = '.ytp-speedmaster-overlay, .ytp-speed-overlay, [class*="speedmaster"], [class*="speed-overlay"] { display: none !important; }';
                                try {
                                    root.appendChild(style);
                                } catch(e) {}
                            }
                        };
                        window._yue_injectStyle = injectStyle; // expose to nested scopes
                        
                        injectStyle(document.head || document.documentElement);

                        try {
                            if (!Element.prototype.__yue_attachShadow_intercepted__) {
                                Element.prototype.__yue_attachShadow_intercepted__ = true;
                                var originalAttachShadow = Element.prototype.attachShadow;
                                if (originalAttachShadow) {
                                    Element.prototype.attachShadow = function(init) {
                                        if (init && init.mode === 'closed') {
                                            init.mode = 'open';
                                        }
                                        var shadowRoot = originalAttachShadow.apply(this, arguments);
                                        try {
                                            injectStyle(shadowRoot);
                                        } catch(e) {}
                                        return shadowRoot;
                                    };
                                }
                            }
                        } catch(e) {}
                    } catch(e) {}

                    if (window.__yue_media_hooked__) return;
                    window.__yue_media_hooked__ = true;

                    // 1. Hook or Mock navigator.mediaSession
                    var actionHandlers = {};
                    var mediaSessionToHook = navigator.mediaSession;

                    if (!mediaSessionToHook) {
                        var pbState = 'none';
                        var meta = null;
                        mediaSessionToHook = {
                            setActionHandler: function(action, handler) {
                                actionHandlers[action] = handler;
                            }
                        };
                        try {
                            Object.defineProperty(mediaSessionToHook, 'metadata', {
                                get: function() { return meta; },
                                set: function(val) {
                                    meta = val;
                                    if (val) {
                                        var title = val.title || '';
                                        var artist = val.artist || '';
                                        var album = val.album || '';
                                        var artworkUrl = '';
                                        if (val.artwork && val.artwork.length > 0) {
                                            var src = val.artwork[0].src || '';
                                            if (src) {
                                                var a = document.createElement('a');
                                                a.href = src;
                                                artworkUrl = a.href;
                                            }
                                        }
                                        if (window.YueMediaSession) {
                                            window.YueMediaSession.updateMetadata(title, artist, album, artworkUrl);
                                        } else {
                                            window._yue_pending_metadata = { title: title, artist: artist, album: album, artworkUrl: artworkUrl };
                                        }
                                    }
                                },
                                configurable: true,
                                enumerable: false
                            });
                            Object.defineProperty(mediaSessionToHook, 'playbackState', {
                                get: function() { return pbState; },
                                set: function(val) {
                                    pbState = val;
                                    if (window.YueMediaSession) {
                                        window.YueMediaSession.updatePlaybackState(val === 'playing');
                                    } else {
                                        window._yue_pending_playback = (val === 'playing');
                                    }
                                },
                                configurable: true,
                                enumerable: false
                            });
                            Object.defineProperty(navigator, 'mediaSession', {
                                value: mediaSessionToHook,
                                writable: true,
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    } else {
                        var originalSetActionHandler = mediaSessionToHook.setActionHandler;
                        mediaSessionToHook.setActionHandler = function(action, handler) {
                            actionHandlers[action] = handler;
                            if (originalSetActionHandler) {
                                try { originalSetActionHandler.apply(this, arguments); } catch(e) {}
                            }
                        };

                        // Primary: Hook on MediaSession.prototype so it affects the native instance
                        var targetProto = (window.MediaSession && window.MediaSession.prototype) || Object.getPrototypeOf(mediaSessionToHook);
                        if (targetProto === Object.prototype) {
                            targetProto = mediaSessionToHook;
                        }
                        var originalMetaDescriptor = Object.getOwnPropertyDescriptor(targetProto, 'metadata');
                        var metaVal = null;

                        try {
                            Object.defineProperty(targetProto, 'metadata', {
                                get: function() {
                                    if (originalMetaDescriptor && originalMetaDescriptor.get) {
                                        try { return originalMetaDescriptor.get.call(this); } catch(e) {}
                                    }
                                    return this.__yue_metadata__ || metaVal;
                                },
                                set: function(val) {
                                    this.__yue_metadata__ = val;
                                    metaVal = val;
                                    if (originalMetaDescriptor && originalMetaDescriptor.set) {
                                        try { originalMetaDescriptor.set.call(this, val); } catch(e) {}
                                    }
                                    if (val) {
                                        var title = val.title || '';
                                        var artist = val.artist || '';
                                        var album = val.album || '';
                                        var artworkUrl = '';
                                        if (val.artwork && val.artwork.length > 0) {
                                            var src = val.artwork[0].src || '';
                                            if (src) {
                                                var a = document.createElement('a');
                                                a.href = src;
                                                artworkUrl = a.href;
                                            }
                                        }
                                        if (window.YueMediaSession) {
                                            window.YueMediaSession.updateMetadata(title, artist, album, artworkUrl);
                                        } else {
                                            window._yue_pending_metadata = { title: title, artist: artist, album: album, artworkUrl: artworkUrl };
                                        }
                                    }
                                },
                                configurable: true,
                                enumerable: false
                            });
                        } catch(e) {
                            // Fallback to instance defineProperty if prototype hook fails
                            try {
                                var instMetaVal = null;
                                Object.defineProperty(mediaSessionToHook, 'metadata', {
                                    get: function() { return this.__yue_metadata__ || instMetaVal; },
                                    set: function(val) {
                                        this.__yue_metadata__ = val;
                                        instMetaVal = val;
                                        if (val) {
                                            var title = val.title || '';
                                            var artist = val.artist || '';
                                            var album = val.album || '';
                                            var artworkUrl = '';
                                            if (val.artwork && val.artwork.length > 0) {
                                                var src = val.artwork[0].src || '';
                                                if (src) {
                                                    var a = document.createElement('a');
                                                    a.href = src;
                                                    artworkUrl = a.href;
                                                }
                                            }
                                            if (window.YueMediaSession) {
                                                window.YueMediaSession.updateMetadata(title, artist, album, artworkUrl);
                                            } else {
                                                window._yue_pending_metadata = { title: title, artist: artist, album: album, artworkUrl: artworkUrl };
                                            }
                                        }
                                    },
                                    configurable: true,
                                    enumerable: false
                                });
                            } catch(err) {}
                        }

                        var originalPbDescriptor = Object.getOwnPropertyDescriptor(targetProto, 'playbackState');
                        var pbVal = 'none';
                        try {
                            Object.defineProperty(targetProto, 'playbackState', {
                                get: function() {
                                    if (originalPbDescriptor && originalPbDescriptor.get) {
                                        try { return originalPbDescriptor.get.call(this); } catch(e) {}
                                    }
                                    return this.__yue_playbackState__ || pbVal;
                                },
                                set: function(val) {
                                    this.__yue_playbackState__ = val;
                                    pbVal = val;
                                    if (originalPbDescriptor && originalPbDescriptor.set) {
                                        try { originalPbDescriptor.set.call(this, val); } catch(e) {}
                                    }
                                    if (window.YueMediaSession) {
                                        window.YueMediaSession.updatePlaybackState(val === 'playing');
                                    } else {
                                        window._yue_pending_playback = (val === 'playing');
                                    }
                                },
                                configurable: true,
                                enumerable: false
                            });
                        } catch(e) {
                            try {
                                var instPbVal = 'none';
                                Object.defineProperty(mediaSessionToHook, 'playbackState', {
                                    get: function() { return this.__yue_playbackState__ || instPbVal; },
                                    set: function(val) {
                                        this.__yue_playbackState__ = val;
                                        instPbVal = val;
                                        if (window.YueMediaSession) {
                                            window.YueMediaSession.updatePlaybackState(val === 'playing');
                                        } else {
                                            window._yue_pending_playback = (val === 'playing');
                                        }
                                    },
                                    configurable: true,
                                    enumerable: false
                                });
                            } catch(err) {}
                        }
                    }

                    try {
                        mediaSessionToHook._actionHandlers = actionHandlers;
                    } catch(e) {}

                    // 3. Real-time document-level media capturing listeners (instant, no interval polling)
                    function handlePlayPause(isPlaying) {
                        if (window.YueMediaSession) {
                            var title = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title) || document.title || 'Video Playback';
                            var artist = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) || window.location.hostname || '';
                            var album = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.album) || '';
                            var artworkUrl = '';
                            if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                                var src = navigator.mediaSession.metadata.artwork[0].src || '';
                                if (src) {
                                    var a = document.createElement('a');
                                    a.href = src;
                                    artworkUrl = a.href;
                                }
                            }
                            window.YueMediaSession.updateMetadata(title, artist, album, artworkUrl);
                            window.YueMediaSession.updatePlaybackState(isPlaying);
                        }
                    }

                    document.addEventListener('play', function(e) {
                        if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            handlePlayPause(true);
                        }
                    }, true);

                    document.addEventListener('playing', function(e) {
                        if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            handlePlayPause(true);
                        }
                    }, true);

                    document.addEventListener('pause', function(e) {
                        if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            handlePlayPause(false);
                        }
                    }, true);

                    document.addEventListener('ended', function(e) {
                        if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            handlePlayPause(false);
                        }
                    }, true);

                    document.addEventListener('seeked', function(e) {
                        if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                            if (window.YueMediaSession && e.target.duration > 0 && isFinite(e.target.duration)) {
                                window.YueMediaSession.updatePositionState(
                                    e.target.duration * 1000,
                                    e.target.currentTime * 1000,
                                    e.target.playbackRate
                                );
                            }
                        }
                    }, true);

                    // 3. Flush pending cached metadata to Kotlin bridge once available
                    function flushPending() {
                        if (window.YueMediaSession) {
                            if (window._yue_pending_metadata) {
                                var m = window._yue_pending_metadata;
                                window.YueMediaSession.updateMetadata(m.title, m.artist, m.album, m.artworkUrl);
                                delete window._yue_pending_metadata;
                            }
                            if (window._yue_pending_playback !== undefined) {
                                window.YueMediaSession.updatePlaybackState(window._yue_pending_playback);
                                delete window._yue_pending_playback;
                            }
                        }
                    }
                    setInterval(flushPending, 300);
                    flushPending();

                    // 4. Initial check for playing videos or audios
                    var initialVideo = document.querySelector('video, audio');
                    if (initialVideo && !initialVideo.paused) {
                        handlePlayPause(true);
                    }

                    // 5. Hold to Speed up Video 2x
                    (function() {
                        try {
                            // Track touch states globally for YouTube speedmaster detection
                            window.__yue_is_touching__ = false;
                            window.__yue_touch_start_time__ = 0;
                            document.addEventListener('touchstart', function(e) {
                                window.__yue_is_touching__ = true;
                                window.__yue_touch_start_time__ = Date.now();
                            }, { passive: true, capture: true });
                            var setTouchingFalse = function() {
                                window.__yue_is_touching__ = false;
                            };
                            document.addEventListener('touchend', setTouchingFalse, { passive: true, capture: true });
                            document.addEventListener('touchcancel', setTouchingFalse, { passive: true, capture: true });

                            var overrideFullscreen = function(proto) {
                                if (!proto || proto.__yue_fullscreen_intercepted__) return;
                                proto.__yue_fullscreen_intercepted__ = true;
                                var originalRequest = proto.requestFullscreen || 
                                                      proto.webkitRequestFullscreen || 
                                                      proto.mozRequestFullScreen || 
                                                      proto.msRequestFullscreen;
                                if (originalRequest) {
                                    var customRequest = function() {
                                        var parent = this.parentElement;
                                        if (parent) {
                                            var parentRequest = parent.requestFullscreen || 
                                                                parent.webkitRequestFullscreen || 
                                                                parent.mozRequestFullScreen || 
                                                                parent.msRequestFullscreen;
                                            if (parentRequest) {
                                                return parentRequest.apply(parent, arguments);
                                            }
                                        }
                                        return originalRequest.apply(this, arguments);
                                    };
                                    proto.requestFullscreen = customRequest;
                                    proto.webkitRequestFullscreen = customRequest;
                                    proto.mozRequestFullScreen = customRequest;
                                    proto.msRequestFullscreen = customRequest;
                                }
                            };
                            overrideFullscreen(HTMLVideoElement.prototype);
                            overrideFullscreen(HTMLMediaElement.prototype);
                        } catch(e) {}

                        var holdTimer = null;
                        var activeVideo = null;
                        var originalPlaybackRate = 1.0;
                        var isSpeedingUp = false;
                        var touchStartX = 0;
                        var touchStartY = 0;
                        var indicator = null;
                        var speedupStartTime = 0;

                        try {
                            if (window.__yue_mediaSessionInitialized__) return;
                            window.__yue_mediaSessionInitialized__ = true;
                            var descriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'playbackRate');
                            if (descriptor && descriptor.set && !window.__yue_playbackRate_intercepted__) {
                                window.__yue_playbackRate_intercepted__ = true;
                                window.__yue_original_set_rate__ = descriptor.set;
                                Object.defineProperty(HTMLMediaElement.prototype, 'playbackRate', {
                                    get: function() {
                                        return descriptor.get.call(this);
                                    },
                                    set: function(val) {
                                        var isYouTubeSpeedmaster = false;
                                        if (window.location.hostname.includes('youtube.com') && val === 2) {
                                            if (window.__yue_is_touching__ && (Date.now() - window.__yue_touch_start_time__ > 200)) {
                                                isYouTubeSpeedmaster = true;
                                            }
                                        }
                                        if (window.__yue_is_speeding_up__ || isYouTubeSpeedmaster) {
                                            if (isYouTubeSpeedmaster && !window.__yue_is_speeding_up__) {
                                                window.__yue_is_speeding_up__ = true;
                                                activeVideo = this;
                                                originalPlaybackRate = descriptor.get.call(this) || 1.0;
                                                if (originalPlaybackRate === 2.0) {
                                                    originalPlaybackRate = 1.0;
                                                }
                                                isSpeedingUp = true;
                                                showIndicator();
                                            }
                                            var targetRate = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupRate) ? parseFloat(YueSettings.getSpeedupRate()) : parseFloat(window.__yue_speedup_rate__ || 2.0);
                                            descriptor.set.call(this, targetRate);
                                        } else {
                                            descriptor.set.call(this, val);
                                        }
                                    },
                                    configurable: true
                                });
                            }
                        } catch(e) {}

                        function formatRate(r) {
                             var s = Number(r).toFixed(2);
                             return s.replace(/\.?0+$/, '');
                         }

                        function showIndicator() {
                             if (window.__yue_in_fullscreen__) return;
                             var container = document.body;
                             if (indicator && indicator.parentNode) {
                                 try { indicator.parentNode.removeChild(indicator); } catch(e) {}
                             }
                             indicator = document.createElement('div');
                             indicator.style.cssText = 'position:fixed;top:16px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,0.25);backdrop-filter:blur(10px);color:rgba(255,255,255,0.9);padding:5px 12px;border-radius:16px;font-family:sans-serif;font-size:11px;font-weight:bold;z-index:2147483647;pointer-events:none;box-shadow:0 2px 8px rgba(0,0,0,0.1);transition:opacity 0.2s;opacity:0;display:flex;align-items:center;gap:4px;letter-spacing:0.5px;';
                             try { container.appendChild(indicator); } catch(e) { document.body.appendChild(indicator); }
                             var rate = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupRate) ? parseFloat(YueSettings.getSpeedupRate()) : (window.__yue_speedup_rate__ || 2.0);
                             var template = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupText) ? YueSettings.getSpeedupText() : (window.__yue_speedup_text__ || '%1${'$'}sx Speed');
                             var displayText = template.replace('%1${'$'}s', formatRate(rate));
                             indicator.innerHTML = displayText + ' <span style="color:#EC4899;opacity:0.9;font-size:13px;font-weight:bold;">&raquo;</span>';
                             indicator.offsetHeight; // force reflow
                             indicator.style.opacity = '1';
                             // Hide YouTube's built-in speed overlay if present (including inside shadow DOMs)
                             try {
                                 var ytSpeedEls = document.querySelectorAll('.ytp-tooltip, .ytp-tip, .ytp-speed-overlay, .ytp-speedmaster-overlay, [class*="speed"]');
                                 for (var i = 0; i < ytSpeedEls.length; i++) {
                                     var el = ytSpeedEls[i];
                                     if (el) {
                                         el.style.setProperty('display', 'none', 'important');
                                     }
                                 }
                             } catch(e) {}
                        }

                        function hideIndicator() {
                             if (indicator) {
                                 indicator.style.opacity = '0';
                             }
                        }

                        var speedupBackupTimer = null;

                        function cancelHold() {
                              // Clear backup timer
                              if (speedupBackupTimer) {
                                  clearTimeout(speedupBackupTimer);
                                  speedupBackupTimer = null;
                              }
                              if (holdTimer) {
                                  clearTimeout(holdTimer);
                                  holdTimer = null;
                              }
                              speedupStartTime = 0;
                              // CRITICAL: Set __yue_is_speeding_up__ to false BEFORE restoring the rate.
                              // The intercepted playbackRate setter checks this flag and if it's still true,
                              // it will force the rate back to the target speedup rate instead of allowing
                              // the restore rate to pass through.
                              if (window.__yue_is_speeding_up__) {
                                  window.__yue_is_speeding_up__ = false;
                                  isSpeedingUp = false;
                              }
                              if (isSpeedingUp && activeVideo) {
                                  var restoreRate = activeVideo.__yue_original_rate__ || originalPlaybackRate || 1.0;
                                  var setter = window.__yue_original_set_rate__ || function(v) { this.playbackRate = v; };
                                  try { setter.call(activeVideo, restoreRate); } catch(e) { activeVideo.playbackRate = restoreRate; }
                                  delete activeVideo.__yue_original_rate__;
                                  activeVideo = null;
                                  hideIndicator();
                              }
                              // Clean up any lingering video elements that have stored rates
                              var videos = findAllVideos();
                              for (var i = 0; i < videos.length; i++) {
                                  var v = videos[i];
                                  if (v.__yue_original_rate__ !== undefined) {
                                      var setter = window.__yue_original_set_rate__ || function(val) { this.playbackRate = val; };
                                      try { setter.call(v, v.__yue_original_rate__); } catch(e) { v.playbackRate = v.__yue_original_rate__; }
                                      delete v.__yue_original_rate__;
                                  }
                              }
                              activeVideo = null;
                              originalPlaybackRate = 1.0;
                         }

                        function findAllVideos(root) {
                            var list = [];
                            var r = root || document;
                            try {
                                if (r.tagName === 'VIDEO') {
                                    list.push(r);
                                }
                                if (r.querySelectorAll) {
                                    var els = r.querySelectorAll('video');
                                    for (var i = 0; i < els.length; i++) {
                                        list.push(els[i]);
                                    }
                                }
                            } catch(e) {}
                            return list;
                        }

                        document.addEventListener('touchstart', function(e) {
                             var isEnabled = (typeof YueSettings !== 'undefined' && YueSettings.isSpeedupEnabled) ? YueSettings.isSpeedupEnabled() : (window.__yue_speedup_enabled__ !== false);
                             if (isEnabled === false) return;
                             if (e.touches.length !== 1) return;
                             var touch = e.touches[0];
                             touchStartX = touch.clientX;
                             touchStartY = touch.clientY;
                             var touchTarget = e.target;
                             if (window.__yue_is_speeding_up__ && speedupStartTime > 0 && (Date.now() - speedupStartTime > 3000)) {
                                 cancelHold();
                             }

                             holdTimer = setTimeout(function() {
                                 var videos = findAllVideos();
                                 var targetVideo = null;

                                 // 1. Check if touch is within any video bounding rect
                                 for (var i = 0; i < videos.length; i++) {
                                     var v = videos[i];
                                     var rect = v.getBoundingClientRect();
                                     if (touchStartX >= rect.left && touchStartX <= rect.right &&
                                         touchStartY >= rect.top && touchStartY <= rect.bottom) {
                                         targetVideo = v;
                                         break;
                                     }
                                 }

                                 // 2. Check if touch target's container/ancestors contain a video
                                 if (!targetVideo && touchTarget) {
                                     var found = findAllVideos(touchTarget);
                                     if (found.length > 0) {
                                         targetVideo = found[0];
                                     } else {
                                         var parent = touchTarget.parentElement;
                                         while (parent && parent !== document.body) {
                                             var pVideos = findAllVideos(parent);
                                             if (pVideos.length > 0) {
                                                 targetVideo = pVideos[0];
                                                 break;
                                             }
                                             parent = parent.parentElement;
                                         }
                                     }
                                 }

                                 // 3. Fallback: find any playing video
                                 if (!targetVideo) {
                                     for (var i = 0; i < videos.length; i++) {
                                         var v = videos[i];
                                         if (!v.paused && !v.ended) {
                                             targetVideo = v;
                                             break;
                                         }
                                     }
                                 }

                                 if (targetVideo) {
                                     activeVideo = targetVideo;
                                     window.__yue_is_speeding_up__ = true;
                                     speedupStartTime = Date.now();
                                     var currentRate = activeVideo.playbackRate || 1.0;
                                     // Store on video element for reliable restore
                                     if (activeVideo.__yue_original_rate__ === undefined) {
                                         activeVideo.__yue_original_rate__ = currentRate;
                                     }
                                     originalPlaybackRate = currentRate;
                                     var targetRate = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupRate) ? parseFloat(YueSettings.getSpeedupRate()) : (window.__yue_speedup_rate__ || 2.0);
                                     var setter = window.__yue_original_set_rate__ || function(v) { this.playbackRate = v; };
                                     try { setter.call(activeVideo, parseFloat(targetRate)); } catch(ex) { activeVideo.playbackRate = parseFloat(targetRate); }
                                     isSpeedingUp = true;
                                     showIndicator();
                                     
                                     // Vibrate
                                     if (navigator.vibrate) {
                                         try { navigator.vibrate(40); } catch(ex) {}
                                     }
                                     
                                     // Backup timeout: auto-restore speed after 10 seconds
                                     // as a safety net in case touchend/touchcancel/pointerup never fires
                                     speedupBackupTimer = setTimeout(function() {
                                         cancelHold();
                                     }, 10000);
                                 }
                             }, 500); // 500ms hold threshold
                        }, { passive: true, capture: true });

                        document.addEventListener('touchmove', function(e) {
                            if (!holdTimer) return;
                            var touch = e.touches[0];
                            var diffX = Math.abs(touch.clientX - touchStartX);
                            var diffY = Math.abs(touch.clientY - touchStartY);
                            var limit = window.location.hostname.includes('youtube.com') ? 100 : 40;
                            if (diffX > limit || diffY > limit) {
                                cancelHold();
                            }
                        }, { passive: true, capture: true });

                        document.addEventListener('touchend', cancelHold, { capture: true });
                        document.addEventListener('touchcancel', cancelHold, { capture: true });
                        
                        // Pointer events as a more reliable fallback for detecting when touch ends
                        // Some video players consume touch events and prevent touchend from reaching
                        // the document, but pointer events are harder to intercept.
                        document.addEventListener('pointerup', function(e) {
                            if (window.__yue_is_speeding_up__ || isSpeedingUp) {
                                cancelHold();
                            }
                        }, { capture: true });
                        document.addEventListener('pointercancel', function(e) {
                            if (window.__yue_is_speeding_up__ || isSpeedingUp) {
                                cancelHold();
                            }
                        }, { capture: true });
                    })();
                } catch(e) {}
            })();
        """.trimIndent()

    val visibilityOverrideScript = """
        (function() {
            try {
                if (window.__yue_visibility_override_hooked__) return;
                window.__yue_visibility_override_hooked__ = true;

                // Helper to check if background play is enabled dynamically (always allow in PiP)
                var isBgPlayEnabled = function() {
                    if (window.__yue_in_pip__) return true;
                    return (typeof YueSettings !== 'undefined' && YueSettings.isBackgroundPlayEnabled) ? 
                        YueSettings.isBackgroundPlayEnabled() : false;
                };

                // Store original descriptors
                var originalHiddenDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden') || {};
                var originalVisStateDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState') || {};
                var originalHasFocus = Document.prototype.hasFocus || document.hasFocus;

                // 1. Override document/prototype visibility properties dynamically
                var overrideProto = function(proto) {
                    try {
                        Object.defineProperty(proto, 'hidden', { 
                            get: function() { 
                                return isBgPlayEnabled() ? false : (originalHiddenDesc.get ? originalHiddenDesc.get.call(this) : false); 
                            }, 
                            configurable: true 
                        });
                        Object.defineProperty(proto, 'visibilityState', { 
                            get: function() { 
                                return isBgPlayEnabled() ? 'visible' : (originalVisStateDesc.get ? originalVisStateDesc.get.call(this) : 'visible'); 
                            }, 
                            configurable: true 
                        });
                        Object.defineProperty(proto, 'webkitHidden', { 
                            get: function() { 
                                return isBgPlayEnabled() ? false : (originalHiddenDesc.get ? originalHiddenDesc.get.call(this) : false); 
                            }, 
                            configurable: true 
                        });
                        Object.defineProperty(proto, 'webkitVisibilityState', { 
                            get: function() { 
                                return isBgPlayEnabled() ? 'visible' : (originalVisStateDesc.get ? originalVisStateDesc.get.call(this) : 'visible'); 
                            }, 
                            configurable: true 
                        });
                    } catch(e) {}
                };
                overrideProto(Document.prototype);
                if (window.HTMLDocument) {
                    overrideProto(HTMLDocument.prototype);
                }
                overrideProto(document);

                // 2. Override document.hasFocus
                try {
                    var customHasFocus = function() {
                        return isBgPlayEnabled() ? true : (originalHasFocus ? originalHasFocus.call(this) : true);
                    };
                    Document.prototype.hasFocus = customHasFocus;
                    document.hasFocus = customHasFocus;
                } catch(e) {}

                // Helper to check if page is actually hidden natively
                var isActuallyHidden = function() {
                    if (originalHiddenDesc && originalHiddenDesc.get) {
                        try {
                            return originalHiddenDesc.get.call(document);
                        } catch(ex) {}
                    }
                    return false;
                };

                // 3. Intercept event dispatching (fail-safe)
                var stopVisibilityEvents = function(e) {
                    if (isBgPlayEnabled() && (isActuallyHidden() || window.__yue_in_pip__)) {
                        if (e && (e.type === 'visibilitychange' || e.type === 'webkitvisibilitychange' || e.type === 'pagehide')) {
                            e.stopImmediatePropagation();
                            e.preventDefault();
                        }
                        if (e && e.type === 'blur') {
                            e.stopImmediatePropagation();
                            e.preventDefault();
                        }
                    }
                };
                document.addEventListener('visibilitychange', stopVisibilityEvents, true);
                document.addEventListener('webkitvisibilitychange', stopVisibilityEvents, true);
                window.addEventListener('pagehide', stopVisibilityEvents, true);
                window.addEventListener('blur', stopVisibilityEvents, true);
                document.addEventListener('blur', stopVisibilityEvents, true);
            } catch(e) {}
        })();
    """.trimIndent()

    fun getSpaBackScript(): String = """
        (function() {
            try {
                if (window.history.length > 1) {
                    window.history.back();
                    return 'back';
                }
                return 'no_history';
            } catch(e) {
                return 'error';
            }
        })();
    """.trimIndent()

    fun getSpaForwardScript(): String = """
        (function() {
            try {
                window.history.forward();
                return 'forward';
            } catch(e) {
                return 'error';
            }
        })();
    """.trimIndent()

    fun getPageTranslationScript(sourceLanguage: String, targetLanguage: String): String {
        val escapedSource = escapeJsString(sourceLanguage)
        val escapedTarget = escapeJsString(targetLanguage)
        return """
            (async function() {
                const sourceLang = '$escapedSource';
                const targetLang = '$escapedTarget';
                const ignoreTags = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'IFRAME', 'TEXTAREA', 'INPUT']);
                
                if (!window.__translationCallbacks) {
                    window.__translationCallbacks = {};
                    window.onTranslationCompleted = function(translatedText, callbackId) {
                        const cb = window.__translationCallbacks[callbackId];
                        if (cb) {
                            cb(translatedText);
                            delete window.__translationCallbacks[callbackId];
                        }
                    };
                    window.onTranslationFailed = function(callbackId) {
                        delete window.__translationCallbacks[callbackId];
                    };
                }
                function isBoilerplate(el) {
                    const ignoreTags = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CODE', 'PRE', 'IFRAME', 'TEXTAREA', 'INPUT', 'OPTION', 'SELECT', 'BUTTON', 'NAV', 'HEADER', 'FOOTER', 'ASIDE']);
                    const ignoreRegex = /(^|[-_ ])(ad|ads|advert|advertisement|sponsored|sponsor|promo|promotion|banner|widget|popup|cookie|consent|nav|navigation|menu|sidebar|header|footer|social|share|sharing|setting|option|config|modal|dialog|privacy|disclaimer)([-_ ]|$)/i;
                    
                    let cur = el;
                    while (cur && cur !== document.body) {
                        if (ignoreTags.has(cur.tagName)) {
                            return true;
                        }
                        const id = cur.id || '';
                        const className = cur.className || '';
                        const classes = typeof className === 'string' ? className : '';
                        if (ignoreRegex.test(id) || ignoreRegex.test(classes)) {
                            return true;
                        }
                        cur = cur.parentElement;
                    }
                    return false;
                }

                function getTranslationUnits() {
                    const elements = Array.from(document.querySelectorAll('p, li, h1, h2, h3, h4, h5, h6, dt, dd')).filter(el => !isBoilerplate(el));
                    const divsAndSpans = Array.from(document.querySelectorAll('div, span, section, article')).filter(el => {
                        if (isBoilerplate(el)) return false;
                        let hasDirectText = false;
                        for (let i = 0; i < el.childNodes.length; i++) {
                            const child = el.childNodes[i];
                            if (child.nodeType === Node.TEXT_NODE && child.textContent.trim().length > 0) {
                                hasDirectText = true;
                                break;
                            }
                        }
                        if (!hasDirectText) return false;
                        const hasChildBlock = el.querySelector('p, li, h1, h2, h3, h4, h5, h6, div, section, article') !== null;
                        return !hasChildBlock;
                    });
                    
                    const all = [...elements, ...divsAndSpans];
                    return all.filter(el => {
                        return !all.some(parent => parent !== el && parent.contains(el));
                    });
                }

                const units = getTranslationUnits();
                if (window.YueAddons && window.YueAddons.translateText) {
                    const debugText = "YUE_DEBUG_DOM: total_units=" + units.length + "\n" +
                        units.map((u, idx) => idx + ": " + u.tagName + " (len=" + u.textContent.trim().length + ") text: " + u.textContent.trim().substring(0, 40)).join("\n");
                    window.YueAddons.translateText(debugText, "auto", "zh", "debug_dom");
                }
                if (units.length === 0) return;

                let currentBatch = [];
                let currentLength = 0;
                const batches = [];

                for (const el of units) {
                    const text = el.textContent.trim();
                    if (text.length === 0) continue;
                    if (currentLength + text.length > 3000) {
                        batches.push(currentBatch);
                        currentBatch = [];
                        currentLength = 0;
                    }
                    currentBatch.push(el);
                    currentLength += text.length;
                }
                if (currentBatch.length > 0) {
                    batches.push(currentBatch);
                }

                function translateTextNative(text) {
                    return new Promise((resolve, reject) => {
                        const callbackId = Math.random().toString(36).substring(2);
                        window.__translationCallbacks[callbackId] = resolve;
                        if (window.YueAddons && window.YueAddons.translateText) {
                            window.YueAddons.translateText(text, sourceLang, targetLang, callbackId);
                        } else {
                            reject("YueAddons not found");
                        }
                    });
                }

                const promises = batches.map(async (batch) => {
                    const texts = batch.map(el => el.textContent.trim());
                    const delimiter = "\n___999888777___\n";
                    const combinedText = texts.join(delimiter);
                    try {
                        const translated = await translateTextNative(combinedText);
                        if (translated) {
                            const translatedTexts = translated.split(/\s*__+\s*999888777\s*__+\s*/);
                            for (let i = 0; i < batch.length; i++) {
                                if (translatedTexts[i]) {
                                    batch[i].textContent = translatedTexts[i].trim();
                                }
                            }
                        }
                    } catch (e) {
                        console.error(e);
                    }
                });
                await Promise.all(promises);
            })();
        """.trimIndent()
    }
}

