package com.yue.browser.data.engine

object WebViewScripts {
    val overlayAdRemoverScript = """
            (function() {
                if (window.yueOverlayRemoverInitialized) return;
                window.yueOverlayRemoverInitialized = true;

                var originalOpen = window.open;
                window.open = function(url, name, specs, replace) {
                    if (!url || url === 'about:blank') {
                        console.log('YueBlock: Blocked empty/suspicious window.open call');
                        return null; 
                    }
                    try {
                        var currentHost = window.location.hostname;
                        var targetUrl = new URL(url, window.location.href);
                        var targetHost = targetUrl.hostname;
                        if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                            var adKeywords = ['click', 'pop', 'ads', 'promo', 'affiliate', 'banner', 'doubleclick', 'onclick', 'redirect', 'bonus', 'gacor', 'slot', 'cuan', '388hero', 'dewa', 'judi', 'togel', 'casino', 'bet', 'poker', 'maxwin', 'scatter', 'dewacuan', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin'];
                            var isAd = adKeywords.some(function(k) { return url.toLowerCase().includes(k); });
                            if (isAd) {
                                console.log('YueBlock: Blocked third-party ad/gambling window.open:', url);
                                return null;
                            }
                        }
                    } catch(e) {}
                    return originalOpen.apply(this, arguments);
                };

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var anchor = target.closest ? target.closest('a') : null;
                    if (anchor) {
                        var href = anchor.getAttribute('href');
                        if (href && !href.startsWith('javascript:') && !href.startsWith('#')) {
                            try {
                                var currentHost = window.location.hostname;
                                var targetUrl = new URL(href, window.location.href);
                                var targetHost = targetUrl.hostname;
                                
                                if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                                    var style = window.getComputedStyle(anchor);
                                    var rect = anchor.getBoundingClientRect();
                                    var viewWidth = window.innerWidth || document.documentElement.clientWidth;
                                    var viewHeight = window.innerHeight || document.documentElement.clientHeight;
                                    
                                    var opacity = parseFloat(style.opacity);
                                    var isTransparent = opacity < 0.1 || style.backgroundColor === 'transparent' || style.color === 'transparent';
                                    var coversLargeArea = (rect.width * rect.height) > (viewWidth * viewHeight * 0.25);
                                    var hasNoText = anchor.textContent.trim().length === 0;
                                    
                                    var isSuspicious = (isTransparent && (coversLargeArea || hasNoText)) || 
                                                       anchor.classList.contains('ad-link') || 
                                                       /click|pop|ads|direct|gacor|slot|bet|judi|togel|casino|poker|maxwin|scatter|cuan|dewacuan|388hero|gaza88|rusia777|kaikoslot|pentaslot|agenjudionline|bandarjudionline|situsjudionline|slotgacor|slotmaxwin/i.test(href);
                                    
                                    if (isSuspicious && coversLargeArea) {
                                        console.log('YueBlock: Blocked clickjack redirect:', href);
                                        e.preventDefault();
                                        e.stopPropagation();
                                        e.stopImmediatePropagation();
                                    }
                                }
                            } catch(err) {}
                        }
                    }
                }, true);

                (function() {
                    var lastScrollTop = 0;
                    var threshold = 25;
                    document.addEventListener('scroll', function(e) {
                        var target = e.target;
                        if (!target) return;
                        var scrollTop = 0;
                        if (target === document || target === window || target === document.documentElement || target === document.body) {
                            scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
                        } else if (target.scrollTop !== undefined) {
                            scrollTop = target.scrollTop;
                        } else {
                            return;
                        }
                        if (scrollTop === 0) {
                            if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                window.YueScroll.onScrollChanged(true);
                            }
                            lastScrollTop = 0;
                            return;
                        }
                        var diff = scrollTop - lastScrollTop;
                        if (Math.abs(diff) > threshold) {
                            if (diff > 0 && scrollTop > 50) {
                                if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                    window.YueScroll.onScrollChanged(false);
                                }
                            } else {
                                if (window.YueScroll && window.YueScroll.onScrollChanged) {
                                    window.YueScroll.onScrollChanged(true);
                                }
                            }
                            lastScrollTop = scrollTop;
                        }
                    }, true);
                })();

                var adPatterns = [
                    /\bad[s]?[-_]?(overlay|popup|modal|banner|layer|wrap|container|box|frame|block|widget|unit|slot)/i,
                    /\b(popup|pop-up|pop_up)[-_]?ad/i,
                    /\b(overlay|modal|interstitial)[-_]?(ad|ads|iklan)/i,
                    /\biklan[-_]?(popup|overlay|modal)/i,
                    /\bads?[-_]?(top|bottom|float|fixed|sticky|layer|floating)/i,
                    /(onesignal|notification-bell|notify-bell|push-bell|bell-launcher|bell-container|propush|webpush|web-push|push-notif|notification-icon)/i,
                    /(float|floating)[-_]?(btn|button|icon|widget|ad)/i,
                    /(fab|floating-action)[-_]?(btn|button)/i
                ];

                function isAdNode(el) {
                    if (!el || el.nodeType !== 1) return false;
                    var id = (el.id || '').toLowerCase();
                    var cls = (el.className && typeof el.className === 'string' ? el.className : '').toLowerCase();
                    var combined = id + ' ' + cls;
                    
                    // Check patterns
                    for (var i = 0; i < adPatterns.length; i++) {
                        if (adPatterns[i].test(combined)) return true;
                    }
                    
                    // Check content/text for bell/notification
                    var text = (el.textContent || '').toLowerCase();
                    if ((text.includes('bell') || text.includes('notif') || text.includes('notification')) && text.length < 50) {
                        return true;
                    }
                    
                    // Check for common ad attribute names
                    var attrs = ['data-ad', 'data-popup', 'data-overlay', 'data-bell', 'data-notification'];
                    for (var a = 0; a < attrs.length; a++) {
                        if (el.hasAttribute(attrs[a])) return true;
                    }
                    
                    return false;
                }

                function removeOverlayAds() {
                    var all = document.querySelectorAll('div, section, aside, iframe, a');
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        try {
                            var style = window.getComputedStyle(el);
                            var pos = style.position;
                            var zIndex = parseInt(style.zIndex) || 0;
                            var w = el.offsetWidth;
                            var h = el.offsetHeight;
                            var isLarge = w > (window.innerWidth * 0.4) && h > 120;
                            var isFixed = (pos === 'fixed' || pos === 'sticky');
                            
                            // Only remove LARGE fixed/sticky overlays that are CLEARLY ads
                            // NEVER remove small icon buttons, FABs, nav icons — too many legit UI elements
                            if (isFixed && zIndex > 100 && isLarge && isAdNode(el)) {
                                el.remove();
                            }
                        } catch (e) {}
                    }
                }

                removeOverlayAds();
                
                // Run less frequently — only a few times on load
                setTimeout(removeOverlayAds, 500);
                setTimeout(removeOverlayAds, 2000);
                setTimeout(removeOverlayAds, 4000);
                setTimeout(removeOverlayAds, 6000);

                // Anti-Adblock Killer & Ad Remover
                function killAntiAdblock() {
                    // Use ONLY very specific gambling keyword patterns — never general words
                    var adKeywords = ['gacor777', 'gacor888', 'gacor999', 'dewacuan', '388hero', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'ratu89', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin', 'slot-gacor', 'bandarxl'];
                    
                    try {
                        var imgs = document.querySelectorAll('img');
                        for (var i = 0; i < imgs.length; i++) {
                            var img = imgs[i];
                            var src = (img.src || '').toLowerCase();
                            var dataSrc = (img.getAttribute('data-src') || '').toLowerCase();
                            var lazySrc = (img.getAttribute('data-lazy-src') || '').toLowerCase();
                            var alt = (img.alt || '').toLowerCase();
                            
                            var isAd = adKeywords.some(function(k) { 
                                return src.includes(k) || dataSrc.includes(k) || lazySrc.includes(k) || alt.includes(k); 
                            });
                            
                            if (isAd) {
                                img.style.setProperty('display', 'none', 'important');
                                // Only hide direct anchor parent — never hide grandparent divs
                                var anchor = img.closest('a');
                                if (anchor) {
                                    anchor.style.setProperty('display', 'none', 'important');
                                }
                            }
                        }
                    } catch (e) {}

                    try {
                        var elements = document.querySelectorAll('div, section, aside, span');
                        for(var i=0; i<elements.length; i++) {
                            var el = elements[i];
                            if (el.querySelectorAll('img').length > 1) continue;
                            
                            if(el.innerText && el.innerText.length < 500) {
                                var text = el.innerText.toLowerCase();
                                if(text.includes('adblock detected') || text.includes('disable your ad blocker') || text.includes('turn off your ad blocker')) {
                                    el.remove();
                                }
                            }
                        }
                    } catch (e) {}

                    if(document.body && document.body.style.overflow === 'hidden') {
                        document.body.style.overflow = '';
                    }
                    if(document.documentElement && document.documentElement.style.overflow === 'hidden') {
                        document.documentElement.style.overflow = '';
                    }

                    try {
                        var links = document.querySelectorAll('a');
                        for(var j=0; j<links.length; j++) {
                            var l = links[j];
                            var href = l.href ? l.href.toLowerCase() : '';
                            var isAdLink = adKeywords.some(function(k) { return href.includes(k); });
                            
                            if (href && isAdLink) {
                                l.style.setProperty('display', 'none', 'important');
                            }
                        }
                    } catch (e) {}
                }
                
                killAntiAdblock();
                setInterval(killAntiAdblock, 2000);

                var observer = new MutationObserver(function(mutations) {
                    for (var m = 0; m < mutations.length; m++) {
                        var added = mutations[m].addedNodes;
                        for (var n = 0; n < added.length; n++) {
                            var node = added[n];
                            if (node.nodeType === 1) {
                                try {
                                    var style = window.getComputedStyle(node);
                                    var pos = style.position;
                                    var zIndex = parseInt(style.zIndex) || 0;
                                    var w = node.offsetWidth || 0;
                                    var h = node.offsetHeight || 0;
                                    var isFixed = (pos === 'fixed' || pos === 'sticky' || pos === 'absolute');
                                    var isSmallSquare = w < 120 && h < 120 && w > 20 && h > 20;
                                    if (isFixed && zIndex > 100 && (isAdNode(node) || (node.tagName === 'IFRAME' && ((w > window.innerWidth * 0.5 && h > 60) || isSmallSquare)))) {
                                        node.remove();
                                    }
                                } catch(e) {}
                            }
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
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
                style.innerHTML = `
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

    fun getPlatformOverrideScript(platformStr: String, expectedUA: String, isMobileVal: Boolean, platformUAData: String): String {
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
                try { Object.defineProperty(navigator, 'platform', { get: function() { return '$platformStr'; } }); } catch(e) {}
                try { Object.defineProperty(navigator, 'userAgent', { get: function() { return '$expectedUA'; } }); } catch(e) {}
                if (navigator.userAgentData) {
                    try { Object.defineProperty(navigator.userAgentData, 'mobile', { get: function() { return $isMobileVal; } }); } catch(e) {}
                    try { Object.defineProperty(navigator.userAgentData, 'platform', { get: function() { return '$platformUAData'; } }); } catch(e) {}
                }
            })();
        """.trimIndent()
    }
}
