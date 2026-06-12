package com.yue.browser.data.engine

object WebViewScripts {
    val overlayAdRemoverScript = """
            (function() {
                if (window.yueOverlayRemoverInitialized) return;
                window.yueOverlayRemoverInitialized = true;

                var originalOpen = window.open;
                window.open = function(url, name, specs, replace) {
                    if (!url || url === 'about:blank') {
                        return null;
                    }
                    try {
                        var currentHost = window.location.hostname;
                        var targetUrl = new URL(url, window.location.href);
                        var targetHost = targetUrl.hostname;
                        if (targetHost && targetHost !== currentHost && !targetHost.endsWith('.' + currentHost)) {
                            var adKeywords = ['click', 'pop', 'ads', 'promo', 'affiliate', 'banner', 'doubleclick', 'onclick', 'redirect', 'bonus', 'gacor', 'slot', 'cuan', '388hero', 'dewa', 'judi', 'togel', 'casino', 'bet', 'poker', 'maxwin', 'scatter', 'dewacuan', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin'];
                            var isAd = false;
                            for (var ak = 0; ak < adKeywords.length; ak++) { if (url.toLowerCase().indexOf(adKeywords[ak]) !== -1) { isAd = true; break; } }
                            if (isAd) return null;
                        }
                    } catch(e) {}
                    return originalOpen.apply(this, arguments);
                };

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
                                    if (coversLargeArea) {
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

                function removeOverlayAds() {
                    try {
                        var all = document.querySelectorAll('div, section, aside, iframe, a');
                        for (var i = 0; i < all.length; i++) {
                            var el = all[i];
                            try {
                                var st = window.getComputedStyle(el);
                                var pos = st.position;
                                var zIndex = parseInt(st.zIndex) || 0;
                                var w = el.offsetWidth;
                                var h = el.offsetHeight;
                                if ((pos === 'fixed' || pos === 'sticky') && zIndex > 100 && w > (window.innerWidth * 0.4) && h > 120) {
                                    var cname = (el.className && typeof el.className === 'string' ? el.className : '').toLowerCase();
                                    var eid = (el.id || '').toLowerCase();
                                    var combined = eid + ' ' + cname;
                                    var adwords = ['ad-', 'ads-', 'advert', 'banner', 'popup', 'pop-up', 'overlay', 'modal', 'iklan', 'promo', 'gacor', 'slot', 'togel', 'judi', 'casino', 'bet', 'poker', 'maxwin', 'scatter', 'cuan', 'dewacuan', '388hero', 'gaza88', 'rusia777', 'kaikoslot', 'pentaslot', 'agenjudionline', 'bandarjudionline', 'situsjudionline', 'slotgacor', 'slotmaxwin'];
                                    var isAd = false;
                                    for (var aw = 0; aw < adwords.length; aw++) { if (combined.indexOf(adwords[aw]) !== -1) { isAd = true; break; } }
                                    if (isAd) el.remove();
                                }
                            } catch (ee) {}
                        }
                    } catch (e) {}
                }

                removeOverlayAds();
                setTimeout(removeOverlayAds, 500);
                setTimeout(removeOverlayAds, 2000);
                setTimeout(removeOverlayAds, 4000);
                setTimeout(removeOverlayAds, 6000);

                function killAntiAdblock() {
                    try {
                        if (document.body && document.body.style.overflow === 'hidden') document.body.style.overflow = '';
                        if (document.documentElement && document.documentElement.style.overflow === 'hidden') document.documentElement.style.overflow = '';
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
                                    var st2 = window.getComputedStyle(node);
                                    var pos2 = st2.position;
                                    var zIndex2 = parseInt(st2.zIndex) || 0;
                                    var w2 = node.offsetWidth || 0;
                                    var h2 = node.offsetHeight || 0;
                                    var isFixed2 = (pos2 === 'fixed' || pos2 === 'sticky' || pos2 === 'absolute');
                                    if (isFixed2 && zIndex2 > 100 && w2 > (window.innerWidth * 0.3) && h2 > 80) {
                                        node.remove();
                                    }
                                } catch(e) {}
                            }
                        }
                    }
                });
                if (document.documentElement) observer.observe(document.documentElement, { childList: true, subtree: true });
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
}
