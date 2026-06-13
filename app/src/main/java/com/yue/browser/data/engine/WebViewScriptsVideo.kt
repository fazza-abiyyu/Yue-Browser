package com.yue.browser.data.engine

object WebViewScriptsVideo {
    val doubleTapScript = """
            (function() {
                if (window.yueDoubleTapSeekingInitialized) return;
                window.yueDoubleTapSeekingInitialized = true;

                var lastTapTime = 0;

                function findVideo(touchTarget) {
                    var el = touchTarget;
                    while (el && el !== document.body) {
                        if (el.tagName === 'VIDEO') return el;
                        el = el.parentElement;
                    }
                    var container = touchTarget.closest
                        ? touchTarget.closest('.jwplayer, [id*="jwplayer"], [class*="jw-"], .video-js, .plyr, [class*="vjs-"], [class*="player"]')
                        : null;
                    if (container) {
                        var v = container.querySelector('video');
                        if (v) return v;
                    }
                    var vids = document.querySelectorAll('video');
                    for (var i = 0; i < vids.length; i++) {
                        if (!vids[i].paused) return vids[i];
                    }
                    return vids[0] || null;
                }

                function getPlayerRect(video) {
                    var rect = video.getBoundingClientRect();
                    if (rect.width > 0) return rect;
                    var node = video.parentElement;
                    while (node && node !== document.body) {
                        rect = node.getBoundingClientRect();
                        if (rect.width > 0) return rect;
                        node = node.parentElement;
                    }
                    return rect;
                }

                function seekVideo(video, clientX) {
                    var rect = getPlayerRect(video);
                    var x = clientX - rect.left;
                    if (x < rect.width / 2) {
                        video.currentTime = Math.max(0, video.currentTime - 5);
                    } else {
                        video.currentTime = Math.min(isFinite(video.duration) ? video.duration : 999999, video.currentTime + 5);
                    }
                }

                function onTouchEnd(e) {
                    var now = Date.now();
                    var diff = now - lastTapTime;
                    lastTapTime = now;

                    if (diff < 400 && diff > 30) {
                        var touch = e.changedTouches ? e.changedTouches[0] : null;
                        if (!touch) return;
                        var video = findVideo(e.target);
                        if (video) {
                            seekVideo(video, touch.clientX);
                            e.stopImmediatePropagation();
                            e.stopPropagation();
                            e.preventDefault();
                            lastTapTime = 0;
                        }
                    }
                }

                function attach(el) {
                    if (el._yueTap) return;
                    el._yueTap = true;
                    el.addEventListener('touchend', onTouchEnd, { passive: false, capture: true });
                }

                function attachToAll() {
                    document.querySelectorAll('video').forEach(attach);
                    document.querySelectorAll([
                        '.jwplayer',
                        '[id*="jwplayer"]',
                        '.jw-overlays',
                        '.jw-media',
                        '.jw-wrapper',
                        '[class*="jw-"]',
                        '.video-js',
                        '.vjs-tech',
                        '.vjs-control-bar',
                        '[class*="vjs-"]',
                        '.plyr',
                        '.plyr__video-wrapper',
                        '[class*="player-container"]',
                        '[class*="video-container"]',
                        '[class*="videoWrapper"]'
                    ].join(',')).forEach(attach);
                }

                attachToAll();

                var observer = new MutationObserver(function() { attachToAll(); });
                if (document.documentElement) observer.observe(document.documentElement, { childList: true, subtree: true });

                document.addEventListener('touchend', onTouchEnd, { passive: false, capture: true });
            })();
        """.trimIndent()

    val elementPickerScript = """
        (function() {
            if (window.__yuePicker__) { window.__yuePicker__.stop(); }
            
            var overlay = null;
            var lastTarget = null;
            var banner = null;
            
            function getCssSelector(el) {
                if (!el || el === document.body) return 'body';
                // Jika elemen punya ID yang stabil, pakai itu langsung
                if (el.id && !/^[0-9]/.test(el.id)) {
                    return el.tagName.toLowerCase() + '#' + CSS.escape(el.id);
                }
                // Kumpulkan class yang bermakna (bukan dinamis/angka)
                var ownClasses = Array.from(el.classList)
                    .filter(function(c) { return c && !c.startsWith('__yue') && !/^[0-9]/.test(c); })
                    .slice(0, 3)
                    .map(function(c) { return '.' + CSS.escape(c); })
                    .join('');
                var tag = el.tagName.toLowerCase();
                if (ownClasses) {
                    // Coba tambahkan konteks parent untuk spesifisitas
                    var parent = el.parentElement;
                    if (parent && parent !== document.body && parent !== document.documentElement) {
                        var pTag = parent.tagName.toLowerCase();
                        var pId = parent.id && !/^[0-9]/.test(parent.id) ? '#' + CSS.escape(parent.id) : '';
                        var pCls = Array.from(parent.classList)
                            .filter(function(c) { return c && !c.startsWith('__yue') && !/^[0-9]/.test(c); })
                            .slice(0, 2)
                            .map(function(c) { return '.' + CSS.escape(c); })
                            .join('');
                        if (pId) return pTag + pId + ' > ' + tag + ownClasses;
                        if (pCls) return pTag + pCls + ' > ' + tag + ownClasses;
                    }
                    return tag + ownClasses;
                }
                // Fallback: path berbasis struktur DOM (bisa pakai nth-of-type)
                var path = [];
                var cur = el;
                while (cur && cur !== document.body && cur !== document) {
                    var curTag = cur.tagName.toLowerCase();
                    var curId = cur.id && !/^[0-9]/.test(cur.id) ? '#' + CSS.escape(cur.id) : '';
                    if (curId) { path.unshift(curTag + curId); break; }
                    var curCls = Array.from(cur.classList)
                        .filter(function(c) { return c && !c.startsWith('__yue') && !/^[0-9]/.test(c); })
                        .slice(0, 2)
                        .map(function(c) { return '.' + CSS.escape(c); })
                        .join('');
                    var sibs = Array.from(cur.parentNode ? cur.parentNode.children : [])
                        .filter(function(s) { return s.tagName === cur.tagName; });
                    var idx = (!curCls && sibs.length > 1) ? ':nth-of-type(' + (sibs.indexOf(cur) + 1) + ')' : '';
                    path.unshift(curTag + curCls + idx);
                    cur = cur.parentElement;
                    if (path.length > 4) break;
                }
                return path.join(' > ');
            }
            
            function showBanner() {
                banner = document.createElement('div');
                banner.id = '__yue_picker_banner__';
                banner.style.cssText = 'display:none;';
                document.body.appendChild(banner);
            }
            
            function highlightElement(el) {
                if (overlay) { overlay.remove(); overlay = null; }
                if (!el || el === document.body || el === document.documentElement) {
                    lastTarget = null;
                    return;
                }
                var rect = el.getBoundingClientRect();
                overlay = document.createElement('div');
                overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;box-sizing:border-box;border:2px solid #e94560;background:rgba(233,69,96,0.12);border-radius:2px;';
                overlay.style.top = rect.top + 'px';
                overlay.style.left = rect.left + 'px';
                overlay.style.width = rect.width + 'px';
                overlay.style.height = rect.height + 'px';
                document.body.appendChild(overlay);
                lastTarget = el;
            }
            
            function mousemoveHandler(e) {
                var el = e.target;
                if (el === banner || (banner && banner.contains(el))) return;
                if (el === overlay) return;
                highlightElement(el);
            }
            
            function clickHandler(e) {
                var el = e.target;
                if (el === banner || (banner && banner.contains(el))) return;
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                if (!el || el === document.body || el === document.documentElement) return;
                var sel = getCssSelector(el);
                window.__yuePicker__.stop();
                if (window.YuePicker) { YuePicker.onPicked(sel); }
            }
            
            function stop() {
                document.removeEventListener('touchstart', touchStartHandler, true);
                document.removeEventListener('mousemove', mousemoveHandler, true);
                document.removeEventListener('touchmove', touchMoveHandler, true);
                document.removeEventListener('click', clickHandler, true);
                document.removeEventListener('touchend', touchEndHandler, true);
                if (overlay) { overlay.remove(); overlay = null; }
                if (banner) { banner.remove(); banner = null; }
                window.__yuePicker__ = null;
            }
            
            function touchStartHandler(e) {
                var touch = e.touches[0];
                var el = document.elementFromPoint(touch.clientX, touch.clientY);
                highlightElement(el);
            }
            
            function touchMoveHandler(e) {
                var touch = e.touches[0];
                var el = document.elementFromPoint(touch.clientX, touch.clientY);
                highlightElement(el);
            }
            
            function touchEndHandler(e) {
                if (!lastTarget || lastTarget === document.body || lastTarget === document.documentElement) return;
                e.preventDefault();
                e.stopPropagation();
                var sel = getCssSelector(lastTarget);
                window.__yuePicker__.stop();
                if (window.YuePicker) { YuePicker.onPicked(sel); }
            }
            
            window.__yuePicker__ = { stop: stop };
            showBanner();
            document.addEventListener('touchstart', touchStartHandler, { passive: true, capture: true });
            document.addEventListener('mousemove', mousemoveHandler, true);
            document.addEventListener('touchmove', touchMoveHandler, { passive: true, capture: true });
            document.addEventListener('click', clickHandler, true);
            document.addEventListener('touchend', touchEndHandler, { passive: false, capture: true });
        })();
    """.trimIndent()
}
