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

                function killAntiAdblock() {
                    try {
                        if (document.body && document.body.style.overflow === 'hidden') document.body.style.overflow = '';
                        if (document.documentElement && document.documentElement.style.overflow === 'hidden') document.documentElement.style.overflow = '';
                    } catch (e) {}
                }

                killAntiAdblock();
                setInterval(killAntiAdblock, 2000);
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
                            var res = origPush.apply(this, arguments);
                            spaDepth++;
                            notifyState();
                            notifySpaDepth();
                            return res;
                        };
                    }
                    var origReplace = window.history.replaceState;
                    if (origReplace) {
                        window.history.replaceState = function() {
                            var res = origReplace.apply(this, arguments);
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

    val mediaSessionScript = """
            (function() {
                try {
                    if (window.__yue_media_hooked__) return;
                    window.__yue_media_hooked__ = true;

                    // 1. Ensure navigator.mediaSession exists and is mocked if not supported natively
                    if (!navigator.mediaSession) {
                        var actionHandlers = {};
                        var meta = null;
                        var pbState = 'none';
                        navigator.mediaSession = {
                            setActionHandler: function(action, handler) {
                                actionHandlers[action] = handler;
                            },
                            _actionHandlers: actionHandlers
                        };
                        Object.defineProperty(navigator.mediaSession, 'metadata', {
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
                            }
                        });
                        Object.defineProperty(navigator.mediaSession, 'playbackState', {
                            get: function() { return pbState; },
                            set: function(val) {
                                pbState = val;
                                if (window.YueMediaSession) {
                                    window.YueMediaSession.updatePlaybackState(val === 'playing');
                                } else {
                                    window._yue_pending_playback = (val === 'playing');
                                }
                            }
                        });
                    }

                    // 2. Real-time document-level media capturing listeners (instant, no interval polling)
                    function handlePlayPause(isPlaying) {
                        if (window.YueMediaSession) {
                            var title = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title) || document.title || 'Video Playback';
                            var artist = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) || window.location.hostname || '';
                            var artworkUrl = '';
                            if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                                var src = navigator.mediaSession.metadata.artwork[0].src || '';
                                if (src) {
                                    var a = document.createElement('a');
                                    a.href = src;
                                    artworkUrl = a.href;
                                }
                            }
                            window.YueMediaSession.updateMetadata(title, artist, '', artworkUrl);
                            window.YueMediaSession.updatePlaybackState(isPlaying);
                        }
                    }

                    document.addEventListener('play', function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') {
                            handlePlayPause(true);
                        }
                    }, true);

                    document.addEventListener('playing', function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') {
                            handlePlayPause(true);
                        }
                    }, true);

                    document.addEventListener('pause', function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') {
                            handlePlayPause(false);
                        }
                    }, true);

                    document.addEventListener('ended', function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') {
                            handlePlayPause(false);
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

                    // 4. Initial check for playing videos
                    var initialVideo = document.querySelector('video');
                    if (initialVideo && !initialVideo.paused) {
                        handlePlayPause(true);
                    }

                    // 5. Hold to Speed up Video 2x
                    (function() {
                        try {
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

                        try {
                            var descriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'playbackRate');
                            if (descriptor && descriptor.set && !window.__yue_playbackRate_intercepted__) {
                                window.__yue_playbackRate_intercepted__ = true;
                                window.__yue_original_set_rate__ = descriptor.set;
                                Object.defineProperty(HTMLMediaElement.prototype, 'playbackRate', {
                                    get: function() {
                                        return descriptor.get.call(this);
                                    },
                                    set: function(val) {
                                        if (window.__yue_is_speeding_up__) {
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

                        var holdTimer = null;
                        var activeVideo = null;
                        var originalPlaybackRate = 1.0;
                        var isSpeedingUp = false;
                        var touchStartX = 0;
                        var touchStartY = 0;
                        var indicator = null;

                         function showIndicator() {
                             var container = document.fullscreenElement || 
                                             document.webkitFullscreenElement || 
                                             (activeVideo && activeVideo.parentElement) || 
                                             document.body;
                             if (!indicator || indicator.parentNode !== container) {
                                 if (indicator && indicator.parentNode) {
                                     try { indicator.parentNode.removeChild(indicator); } catch(e) {}
                                 }
                                 indicator = document.createElement('div');
                                 indicator.style.cssText = 'position:fixed;top:16px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,0.25);backdrop-filter:blur(10px);color:rgba(255,255,255,0.9);padding:5px 12px;border-radius:16px;font-family:sans-serif;font-size:11px;font-weight:bold;z-index:2147483647;pointer-events:none;box-shadow:0 2px 8px rgba(0,0,0,0.1);transition:opacity 0.2s;opacity:0;display:flex;align-items:center;gap:4px;letter-spacing:0.5px;';
                                 try { container.appendChild(indicator); } catch(e) { document.body.appendChild(indicator); }
                             }
                             var rate = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupRate) ? parseFloat(YueSettings.getSpeedupRate()) : (window.__yue_speedup_rate__ || 2.0);
                             var template = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupText) ? YueSettings.getSpeedupText() : (window.__yue_speedup_text__ || '%1${'$'}sx Speed');
                             var displayText = template.replace('%1${'$'}s', rate);
                             indicator.innerHTML = displayText + ' <span style="color:#EC4899;opacity:0.9;font-size:13px;font-weight:bold;">&raquo;</span>';
                             indicator.offsetHeight; // force reflow
                             indicator.style.opacity = '1';
                         }

                         function hideIndicator() {
                             if (indicator) {
                                 indicator.style.opacity = '0';
                             }
                         }

                         function cancelHold() {
                              if (holdTimer) {
                                  clearTimeout(holdTimer);
                                  holdTimer = null;
                              }
                              if (isSpeedingUp && activeVideo) {
                                  window.__yue_is_speeding_up__ = false;
                                  var setter = window.__yue_original_set_rate__ || function(v) { this.playbackRate = v; };
                                  try { setter.call(activeVideo, originalPlaybackRate); } catch(e) { activeVideo.playbackRate = originalPlaybackRate; }
                                  isSpeedingUp = false;
                                  hideIndicator();
                              }
                              activeVideo = null;
                         }

                        function findAllVideos(root) {
                            var list = [];
                            if (!root) return list;
                            if (root.tagName === 'VIDEO') {
                                list.push(root);
                            }
                            var childNodes = root.children || root.childNodes;
                            if (childNodes) {
                                for (var i = 0; i < childNodes.length; i++) {
                                    var node = childNodes[i];
                                    if (node.nodeType === 1) {
                                        list = list.concat(findAllVideos(node));
                                    }
                                }
                            }
                            if (root.shadowRoot) {
                                list = list.concat(findAllVideos(root.shadowRoot));
                            }
                            return list;
                        }

                        document.addEventListener('touchstart', function(e) {
                             var isEnabled = (typeof YueSettings !== 'undefined' && YueSettings.isSpeedupEnabled) ? YueSettings.isSpeedupEnabled() : (window.__yue_speedup_enabled__ !== false);
                             if (isEnabled === false) return;
                             if (e.touches.length !== 1) return;
                            var touch = e.touches[0];
                            touchStartX = touch.clientX;
                            touchStartY = touch.clientY;

                            var videos = findAllVideos(document.documentElement);
                            var targetVideo = null;

                            // 1. Check if touch is within any video bounding rect
                            for (var i = 0; i < videos.length; i++) {
                                var v = videos[i];
                                var rect = v.getBoundingClientRect();
                                if (touch.clientX >= rect.left && touch.clientX <= rect.right &&
                                    touch.clientY >= rect.top && touch.clientY <= rect.bottom) {
                                    targetVideo = v;
                                    break;
                                }
                            }

                            // 2. Check if touch target's container/ancestors contain a video
                            if (!targetVideo && e.target) {
                                var found = findAllVideos(e.target);
                                if (found.length > 0) {
                                    targetVideo = found[0];
                                } else {
                                    var parent = e.target.parentElement;
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
                                holdTimer = setTimeout(function() {
                                     window.__yue_is_speeding_up__ = true;
                                     originalPlaybackRate = activeVideo.playbackRate || 1.0;
                                     var targetRate = (typeof YueSettings !== 'undefined' && YueSettings.getSpeedupRate) ? parseFloat(YueSettings.getSpeedupRate()) : (window.__yue_speedup_rate__ || 2.0);
                                     var setter = window.__yue_original_set_rate__ || function(v) { this.playbackRate = v; };
                                     try { setter.call(activeVideo, parseFloat(targetRate)); } catch(e) { activeVideo.playbackRate = parseFloat(targetRate); }
                                     isSpeedingUp = true;
                                     showIndicator();
                                     
                                     // Vibrate
                                     if (navigator.vibrate) {
                                         try { navigator.vibrate(40); } catch(ex) {}
                                     }
                                 }, 500); // 500ms hold threshold
                            }
                        }, { passive: true, capture: true });

                        document.addEventListener('touchmove', function(e) {
                            if (!holdTimer) return;
                            var touch = e.touches[0];
                            var diffX = Math.abs(touch.clientX - touchStartX);
                            var diffY = Math.abs(touch.clientY - touchStartY);
                            if (diffX > 15 || diffY > 15) {
                                cancelHold();
                            }
                        }, { passive: true, capture: true });

                        document.addEventListener('touchend', cancelHold, { capture: true });
                        document.addEventListener('touchcancel', cancelHold, { capture: true });
                    })();
                } catch(e) {}
            })();
        """.trimIndent()

    val visibilityOverrideScript = """
        (function() {
            try {
                window.__yue_allow_pause = true;
                Object.defineProperty(document, 'hidden', { value: false, writable: false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false, configurable: true });
                document.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);
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
}

