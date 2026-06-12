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
                var path = [];
                var cur = el;
                while (cur && cur !== document.body && cur !== document) {
                    var tag = cur.tagName.toLowerCase();
                    var id = cur.id ? '#' + CSS.escape(cur.id) : '';
                    if (id) {
                        path.unshift(tag + id);
                        break;
                    }
                    var cls = Array.from(cur.classList)
                        .filter(function(c) { return c && !c.startsWith('__yue'); })
                        .slice(0, 2)
                        .map(function(c) { return '.' + CSS.escape(c); })
                        .join('');
                    var sibs = Array.from(cur.parentNode ? cur.parentNode.children : []).filter(function(s) { return s.tagName === cur.tagName; });
                    var idx = sibs.length > 1 ? ':nth-of-type(' + (sibs.indexOf(cur) + 1) + ')' : '';
                    path.unshift(tag + cls + idx);
                    cur = cur.parentElement;
                    if (path.length > 5) break;
                }
                return path.join(' > ');
            }
            
            function showBanner() {
                banner = document.createElement('div');
                banner.id = '__yue_picker_banner__';
                banner.style.cssText = 'display:none;';
                document.body.appendChild(banner);
            }
            
            function mousemoveHandler(e) {
                var el = e.target;
                if (el === banner || (banner && banner.contains(el))) return;
                if (el === overlay) return;
                if (overlay) { overlay.remove(); overlay = null; }
                if (!el || el === document.body || el === document.documentElement) return;
                
                var rect = el.getBoundingClientRect();
                overlay = document.createElement('div');
                overlay.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;box-sizing:border-box;border:2px solid #e94560;background:rgba(233,69,96,0.12);transition:none;border-radius:2px;';
                overlay.style.top = rect.top + 'px';
                overlay.style.left = rect.left + 'px';
                overlay.style.width = rect.width + 'px';
                overlay.style.height = rect.height + 'px';
                document.body.appendChild(overlay);
                lastTarget = el;
            }
            
            function clickHandler(e) {
                var el = e.target;
                if (el === banner || (banner && banner.contains(el))) return;
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                if (!el || el === document.body) return;
                var sel = getCssSelector(el);
                window.__yuePicker__.stop();
                if (window.YuePicker) { YuePicker.onPicked(sel); }
            }
            
            function stop() {
                document.removeEventListener('mousemove', mousemoveHandler, true);
                document.removeEventListener('touchmove', touchMoveHandler, true);
                document.removeEventListener('click', clickHandler, true);
                document.removeEventListener('touchend', touchEndHandler, true);
                if (overlay) { overlay.remove(); overlay = null; }
                if (banner) { banner.remove(); banner = null; }
                window.__yuePicker__ = null;
            }
            
            function touchMoveHandler(e) {
                var touch = e.touches[0];
                var el = document.elementFromPoint(touch.clientX, touch.clientY);
                if (!el || el === banner || (banner && banner.contains(el))) return;
                if (overlay) { overlay.remove(); overlay = null; }
                if (el === document.body || el === document.documentElement) return;
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
            
            function touchEndHandler(e) {
                e.preventDefault();
                e.stopPropagation();
                if (!lastTarget || lastTarget === document.body) return;
                var sel = getCssSelector(lastTarget);
                window.__yuePicker__.stop();
                if (window.YuePicker) { YuePicker.onPicked(sel); }
            }
            
            window.__yuePicker__ = { stop: stop };
            showBanner();
            document.addEventListener('mousemove', mousemoveHandler, true);
            document.addEventListener('touchmove', touchMoveHandler, { passive: true, capture: true });
            document.addEventListener('click', clickHandler, true);
            document.addEventListener('touchend', touchEndHandler, { passive: false, capture: true });
        })();
    """.trimIndent()
}
