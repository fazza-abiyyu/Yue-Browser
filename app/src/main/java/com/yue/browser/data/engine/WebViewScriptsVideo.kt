package com.yue.browser.data.engine

object WebViewScriptsVideo {
    fun elementPickerScript(
        isDark: Boolean,
        labelHapus: String = "Delete",
        labelSelected: String = "%d selected",
        labelHint: String = "Tap element to block"
    ): String {
        val C_bgColor = if (isDark) "#000000" else "#FFFFFF"
        val C_borderColor = if (isDark) "rgba(236,72,153,0.5)" else "#D8D8DC"
        val C_countColor = "#EC4899"
        val C_btnBg = "#EC4899"
        val C_btnText = "#FFFFFF"
        val C_hapusOpacity = "0.5"
        val C_closeColor = if (isDark) "#9AA0A6" else "#4D6172"
        val C_hintColor = if (isDark) "#9AA0A6" else "#4D6172"
        val C_hoverBg = if (isDark) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.06)"
        return """
        (function() {
            window.__yuePickerActive__ = true;
            if (window.__yuePicker__) { window.__yuePicker__.stop(); }
            console.log('YuePicker: init, window.YuePicker=' + (typeof window.YuePicker));

            var selected = [];
            var selectionOverlays = [];
            var previewOverlay = null;
            var lastTouchTarget = null;
            var toolbar = null;
            var overlay = null;
            var deadStyle = null;
            var reorderInterval = null;

            function isUtilityClass(c) {
                var prefixes = [
                    'w-', 'h-', 'min-w-', 'max-w-', 'min-h-', 'max-h-',
                    'items-', 'justify-', 'content-', 'self-',
                    'p-', 'px-', 'py-', 'pt-', 'pb-', 'pl-', 'pr-',
                    'm-', 'mx-', 'my-', 'mt-', 'mb-', 'ml-', 'mr-',
                    'gap-', 'space-', 'bg-', 'text-', 'border-', 'rounded-', 'shadow-',
                    'overflow-', 'z-', 'transition', 'duration-', 'ease-',
                    'select-', 'pointer-', 'opacity-', 'col-', 'row-', 'flex', 'grid',
                    'relative', 'absolute', 'fixed', 'static', 'sticky', 'block', 'hidden',
                    'inline', 'cursor-', 'active:', 'hover:', 'focus:', 'md:', 'lg:', 'sm:', 'xl:'
                ];
                for (var i = 0; i < prefixes.length; i++) {
                    var p = prefixes[i];
                    if (p.endsWith('-') && c.startsWith(p)) return true;
                    if (p.endsWith(':') && c.indexOf(p) !== -1) return true;
                    if (c === p) return true;
                }
                return false;
            }

            function getClasses(el) {
                var result = [];
                if (el && el.classList) {
                    for (var i = 0; i < el.classList.length; i++) {
                        var c = el.classList[i];
                        if (c && c.indexOf('__yue') === -1 && !/^[0-9]/.test(c)) {
                            result.push(c);
                        }
                    }
                }
                result.sort(function(a, b) {
                    var aUtil = isUtilityClass(a);
                    var bUtil = isUtilityClass(b);
                    if (aUtil && !bUtil) return 1;
                    if (!aUtil && bUtil) return -1;
                    return 0;
                });
                return result;
            }

            function escapeCss(str) {
                if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(str);
                return str.replace(/[!"#${'$'}%&'()*+,.\/:;<=>?@[\]^`{|}~ ]/g, '\\${'$'}&');
            }

            function getCssSelector(el) {
                if (!el || el === document.body) return 'body';
                if (el.id && !/^[0-9]/.test(el.id)) {
                    return el.tagName.toLowerCase() + '#' + escapeCss(el.id);
                }
                var tag = el.tagName.toLowerCase();
                var adAttributes = ['data-position', 'data-ad-slot', 'data-ad-client', 'data-slot', 'data-ad', 'data-ad-id'];
                for (var i = 0; i < adAttributes.length; i++) {
                    var attr = adAttributes[i];
                    var val = el.getAttribute(attr);
                    if (val) {
                        return tag + '[' + attr + '="' + escapeCss(val) + '"]';
                    }
                }
                var ownClasses = getClasses(el);
                if (ownClasses.length > 0 && !isUtilityClass(ownClasses[0])) {
                    return tag + '.' + escapeCss(ownClasses[0]);
                }
                if (ownClasses.length > 0) {
                    var clsStr = '';
                    var limit = ownClasses.length > 3 ? 3 : ownClasses.length;
                    for (var i = 0; i < limit; i++) { clsStr += '.' + escapeCss(ownClasses[i]); }
                    var parent = el.parentElement;
                    if (parent && parent !== document.body && parent !== document.documentElement) {
                        var pTag = parent.tagName.toLowerCase();
                        var pId = parent.id && !/^[0-9]/.test(parent.id) ? '#' + escapeCss(parent.id) : '';
                        for (var i = 0; i < adAttributes.length; i++) {
                            var attr = adAttributes[i];
                            var val = parent.getAttribute(attr);
                            if (val) {
                                return pTag + '[' + attr + '="' + escapeCss(val) + '"] > ' + tag;
                            }
                        }
                        var pCls = getClasses(parent);
                        var pClsStr = '';
                        var pLimit = pCls.length > 2 ? 2 : pCls.length;
                        for (var i = 0; i < pLimit; i++) { pClsStr += '.' + escapeCss(pCls[i]); }
                        if (pId) return pTag + pId + ' > ' + tag + clsStr;
                        if (pClsStr) return pTag + pClsStr + ' > ' + tag + clsStr;
                    }
                    return tag + clsStr;
                }
                var path = [];
                var cur = el;
                while (cur && cur !== document.body && cur !== document) {
                    var curTag = cur.tagName.toLowerCase();
                    var curId = cur.id && !/^[0-9]/.test(cur.id) ? '#' + escapeCss(cur.id) : '';
                    if (curId) { path.unshift(curTag + curId); break; }
                    var curCls = getClasses(cur);
                    var curClsStr = '';
                    var limit = curCls.length > 2 ? 2 : curCls.length;
                    for (var i = 0; i < limit; i++) { curClsStr += '.' + escapeCss(curCls[i]); }
                    var siblings = [];
                    if (cur.parentNode && cur.parentNode.children) {
                        var children = cur.parentNode.children;
                        for (var s = 0; s < children.length; s++) {
                            if (children[s].tagName === cur.tagName) siblings.push(children[s]);
                        }
                    }
                    var idx = (!curClsStr && siblings.length > 1) ? ':nth-of-type(' + (siblings.indexOf(cur) + 1) + ')' : '';
                    path.unshift(curTag + curClsStr + idx);
                    cur = cur.parentElement;
                    if (path.length > 4) break;
                }
                return path.join(' > ');
            }

            function buildToolbar() {
                var style = document.getElementById('__yue_picker_style__');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '__yue_picker_style__';
                    style.textContent = `
                        #__yue_picker_toolbar__ {
                            position: fixed;
                            bottom: 24px;
                            left: 50%;
                            transform: translateX(-50%);
                            z-index: 2147483647;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            gap: 8px;
                            background-color: BG_COLOR;
                            border: 1.5px solid BORDER_COLOR;
                            border-radius: 12px;
                            padding: 10px 16px;
                            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                            box-sizing: border-box;
                        }
                        .__yue_picker_row__ {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 16px;
                            width: 100%;
                            box-sizing: border-box;
                        }
                        #__yue_picker_count__ {
                            color: COUNT_COLOR;
                            font-size: 13px;
                            font-weight: bold;
                            min-width: 60px;
                            text-align: left;
                            user-select: none;
                        }
                        #__yue_picker_hapus__ {
                            background-color: BG_BTN;
                            color: BTN_TEXT;
                            border: none;
                            border-radius: 8px;
                            height: 32px;
                            padding: 0 16px;
                            font-size: 12px;
                            font-weight: 600;
                            cursor: pointer;
                            opacity: HAPUS_OPACITY;
                            transition: opacity 0.2s ease, transform 0.1s ease;
                            user-select: none;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            box-sizing: border-box;
                        }
                        #__yue_picker_hapus__:active {
                            transform: scale(0.95);
                        }
                        #__yue_picker_close__ {
                            background: transparent;
                            color: CLOSE_COLOR;
                            border: none;
                            border-radius: 50%;
                            width: 32px;
                            height: 32px;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 16px;
                            font-weight: normal;
                            cursor: pointer;
                            user-select: none;
                            transition: background-color 0.2s ease, transform 0.1s ease;
                            box-sizing: border-box;
                        }
                        #__yue_picker_close__:hover {
                            background-color: HOVER_BG;
                        }
                        #__yue_picker_close__:active {
                            transform: scale(0.9);
                        }
                        #__yue_picker_hint__ {
                            color: HINT_COLOR;
                            font-size: 11px;
                            font-weight: 500;
                            text-align: center;
                            user-select: none;
                        }
                    `;
                    document.head.appendChild(style);
                }

                toolbar = document.createElement('div');
                toolbar.id = '__yue_picker_toolbar__';

                var row = document.createElement('div');
                row.className = '__yue_picker_row__';

                var count = document.createElement('span');
                count.id = '__yue_picker_count__';
                count.textContent = '$labelSelected'.replace('%1${'$'}d', '0').replace('%${'$'}d', '0');
                row.appendChild(count);

                var hapus = document.createElement('button');
                hapus.id = '__yue_picker_hapus__';
                hapus.textContent = '$labelHapus';
                hapus.disabled = true;
                hapus.onclick = function() { submitSelection(); };
                row.appendChild(hapus);

                var close = document.createElement('button');
                close.id = '__yue_picker_close__';
                close.textContent = '\u2715';
                close.onclick = function() { cancelPicker(); };
                row.appendChild(close);

                toolbar.appendChild(row);

                var hint = document.createElement('div');
                hint.id = '__yue_picker_hint__';
                hint.textContent = '$labelHint';
                toolbar.appendChild(hint);

                document.documentElement.appendChild(toolbar);
            }

            function buildOverlay() {
                var style = document.getElementById('__yue_picker_overlay_style__');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '__yue_picker_overlay_style__';
                    style.textContent = `
                        #__yue_picker_overlay__ {
                            position: fixed;
                            top: 0;
                            left: 0;
                            width: 100%;
                            height: 100%;
                            z-index: 2147483647;
                            background: rgba(0,0,0,0.01) !important;
                            user-select: none;
                            -webkit-user-select: none;
                            touch-action: none;
                            box-sizing: border-box;
                            pointer-events: auto;
                        }
                    `;
                    document.head.appendChild(style);
                }
                overlay = document.createElement('div');
                overlay.id = '__yue_picker_overlay__';
                overlay.style.cssText = 'position:fixed !important;top:0 !important;left:0 !important;width:100% !important;height:100% !important;z-index:2147483647 !important;background:rgba(0,0,0,0.01) !important;user-select:none !important;-webkit-user-select:none !important;touch-action:none !important;box-sizing:border-box !important;pointer-events:auto;';
                document.documentElement.appendChild(overlay);

                try {
                    console.log('YuePicker: overlay bounds rect=' + JSON.stringify(overlay.getBoundingClientRect()));
                    console.log('YuePicker: overlay computedStyle=' + window.getComputedStyle(overlay).position + ' zIndex=' + window.getComputedStyle(overlay).zIndex + ' pointerEvents=' + window.getComputedStyle(overlay).pointerEvents);
                } catch(e) {}
 
                deadStyle = document.createElement('style');
                deadStyle.id = '__yue_picker_dead_style__';
                deadStyle.textContent = 'iframe, frame, object, embed { pointer-events: none !important; } #__yue_picker_overlay__, #__yue_picker_toolbar__, #__yue_picker_toolbar__ * { pointer-events: auto !important; }';
                document.head.appendChild(deadStyle);

                // Register on document in capture phase via hooked addEventListener.
                // The hook wraps and registers via native bound method.
                document.addEventListener('mousemove', mousemoveHandler, true);
                document.addEventListener('click', clickHandler, true);
                document.addEventListener('touchstart', touchStartHandler, { passive: false, capture: true });
                document.addEventListener('touchmove', touchMoveHandler, { passive: false, capture: true });
                document.addEventListener('touchend', touchEndHandler, { passive: false, capture: true });

                // Also register on overlay as a fallback (target phase)
                overlay.addEventListener('mousemove', mousemoveHandler);
                overlay.addEventListener('click', clickHandler);
                overlay.addEventListener('touchstart', touchStartHandler, { passive: false });
                overlay.addEventListener('touchmove', touchMoveHandler, { passive: false });
                overlay.addEventListener('touchend', touchEndHandler, { passive: false });
            }

            function generateRobustSelectors(el) {
                var result = [];
                console.log('YuePicker: genRobust start tag=' + el.tagName);

                // 1. Check if there is an ad-like parent container first
                var p = el;
                var adAttributes = ['data-position', 'data-ad-slot', 'data-ad-client', 'data-slot', 'data-ad', 'data-ad-id'];
                while (p && p !== document.body && p !== document.documentElement) {
                    var pTag = p.tagName.toLowerCase();
                    var pId = (p.id || '').toLowerCase();
                    var pClasses = getClasses(p); // sorted unique first
                    var isAdContainer = false;
                    var selStr = '';

                    for (var i = 0; i < adAttributes.length; i++) {
                        var attr = adAttributes[i];
                        var val = p.getAttribute(attr);
                        if (val) {
                            selStr = pTag + '[' + attr + '="' + escapeCss(val) + '"]';
                            isAdContainer = true;
                            break;
                        }
                    }
                    if (!isAdContainer && pId) {
                        if (pId.indexOf('ad') !== -1 || pId.indexOf('sponsor') !== -1 || pId.indexOf('banner') !== -1 || pId.indexOf('partner') !== -1 || pId.indexOf('promo') !== -1) {
                            selStr = pTag + '#' + escapeCss(p.id);
                            isAdContainer = true;
                        }
                    }
                    if (!isAdContainer && pClasses.length > 0) {
                        var c = pClasses[0].toLowerCase();
                        if (c.indexOf('ad-') === 0 || c.indexOf('-ad') !== -1 || c.indexOf('adbox') !== -1 || c.indexOf('adcontainer') !== -1 ||
                            c.indexOf('sponsor') !== -1 || c.indexOf('banner') !== -1 || c.indexOf('partner') !== -1 || c.indexOf('promo') !== -1 ||
                            c.indexOf('slot') !== -1) {
                            selStr = pTag + '.' + escapeCss(pClasses[0]);
                            isAdContainer = true;
                        }
                    }
                    if (isAdContainer && selStr) {
                        result.push(selStr);
                        console.log('YuePicker: Found ad-container parent, returning only: ' + selStr);
                        return result;
                    }
                    p = p.parentElement;
                }

                // 2. Fallback to exact selector if no ad-like parent container found
                var exact = getCssSelector(el);
                console.log('YuePicker: genRobust exact=' + exact);
                result.push(exact);
                return result;
            }

            function submitSelection() {
                if (selected.length === 0) { console.log('YuePicker: submit - empty selection'); return; }
                console.log('YuePicker: submitSelection count=' + selected.length);
                var selectors;
                try {
                    selectors = [];
                    for (var i = 0; i < selected.length; i++) {
                        var elSelectors = generateRobustSelectors(selected[i]);
                        console.log('YuePicker: sel[' + i + ']=' + JSON.stringify(elSelectors));
                        for (var k = 0; k < elSelectors.length; k++) {
                            selectors.push(elSelectors[k]);
                        }
                    }
                } catch(e) {
                    console.error('YuePicker: getCssSelector error', e);
                    return;
                }
                var json = JSON.stringify(selectors);
                console.log('YuePicker: submitting json=' + json);
                prompt('__YuePicker__', json);
                console.log('YuePicker: stopping picker');
                window.__yuePicker__.stop();
            }

            function cancelPicker() {
                console.log('YuePicker: cancelPicker');
                prompt('__YuePickerCancel__', '');
                if (window.__yuePicker__) {
                    console.log('YuePicker: cancel - stopping picker');
                    window.__yuePicker__.stop();
                }
            }

            // Arahkan pemilihan
            function updateUI() {
                var count = document.getElementById('__yue_picker_count__');
                if (count) count.textContent = '$labelSelected'.replace('%1${'$'}d', selected.length.toString()).replace('%${'$'}d', selected.length.toString());
                var hapus = document.getElementById('__yue_picker_hapus__');
                if (hapus) {
                    hapus.style.opacity = selected.length > 0 ? '1' : '0.5';
                    hapus.disabled = selected.length === 0;
                }
            }

            function buildElementOverlay(el, color, bgColor) {
                var rect = el.getBoundingClientRect();
                var div = document.createElement('div');
                div.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483645;box-sizing:border-box;border:2px solid ' + color + ';background:' + bgColor + ';border-radius:2px;top:' + rect.top + 'px;left:' + rect.left + 'px;width:' + rect.width + 'px;height:' + rect.height + 'px;';
                document.body.appendChild(div);
                return div;
            }

            function refreshSelectionOverlays() {
                for (var i = 0; i < selectionOverlays.length; i++) {
                    selectionOverlays[i].remove();
                }
                selectionOverlays = [];
                for (var i = 0; i < selected.length; i++) {
                    if (selected[i] && selected[i].getBoundingClientRect) {
                        selectionOverlays.push(buildElementOverlay(selected[i], '#EC4899', 'rgba(236,72,153,0.15)'));
                    }
                }
            }

            function showPreview(el) {
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                if (!el || el === document.body || el === document.documentElement || isToolbarChild(el)) return;
                if (isSelected(el)) return;
                previewOverlay = buildElementOverlay(el, '#00B4D8', 'rgba(0,180,216,0.08)');
            }

            function hidePreview() {
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
            }

            // Deteksi menu/toolbar
            function isToolbarChild(el) {
                return toolbar && (el === toolbar || toolbar.contains(el));
            }

            function isSelected(el) {
                for (var i = 0; i < selected.length; i++) {
                    if (selected[i] === el) return true;
                }
                return false;
            }

            function toggleElement(el) {
                if (!el || el === document.body || el === document.documentElement || isToolbarChild(el)) return;
                var idx = -1;
                for (var i = 0; i < selected.length; i++) {
                    if (selected[i] === el) { idx = i; break; }
                }
                if (idx !== -1) {
                    selected.splice(idx, 1);
                } else {
                    selected.push(el);
                }
                refreshSelectionOverlays();
                updateUI();
            }

            function getPointElement(x, y) {
                if (!overlay) return null;
                overlay.style.setProperty('pointer-events', 'none', 'important');
                if (deadStyle) deadStyle.disabled = true;
                var disabledElements = [];
                try {
                    var el = document.elementFromPoint(x, y);
                    
                    // Fallback to manual hit-testing via bounding client rect if we hit body, html, or null
                    if (!el || el === document.body || el === document.documentElement) {
                        var candidates = [];
                        var all = document.getElementsByTagName('*');
                        for (var i = 0; i < all.length; i++) {
                            var cand = all[i];
                            if (cand === document.body || cand === document.documentElement || isToolbarChild(cand) || cand.id === '__yue_picker_overlay__') {
                                continue;
                            }
                            var rect = cand.getBoundingClientRect();
                            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                                var area = rect.width * rect.height;
                                if (area > 0) {
                                    candidates.push({ element: cand, area: area });
                                }
                            }
                        }
                        if (candidates.length > 0) {
                            candidates.sort(function(a, b) { return a.area - b.area; });
                            el = candidates[0].element;
                        }
                    }

                    var depth = 0;
                    var winW = window.innerWidth || document.documentElement.clientWidth;
                    var winH = window.innerHeight || document.documentElement.clientHeight;
                    while (el && depth < 8) {
                        if (el === document.body || el === document.documentElement) {
                            break;
                        }
                        if (isToolbarChild(el) || el.id === '__yue_picker_overlay__') {
                            break;
                        }
                        var rect = el.getBoundingClientRect();
                        var isLarge = (rect.width >= winW * 0.8 && rect.height >= winH * 0.8);
                        if (isLarge) {
                            var origPE = el.style.pointerEvents;
                            el.style.setProperty('pointer-events', 'none', 'important');
                            disabledElements.push({ element: el, val: origPE });
                            var nextEl = document.elementFromPoint(x, y);
                            
                            if (!nextEl || nextEl === document.body || nextEl === document.documentElement || nextEl === el) {
                                var candidates = [];
                                var all = document.getElementsByTagName('*');
                                for (var i = 0; i < all.length; i++) {
                                    var cand = all[i];
                                    if (cand === document.body || cand === document.documentElement || isToolbarChild(cand) || cand.id === '__yue_picker_overlay__') {
                                        continue;
                                    }
                                    var isAlreadyDisabled = false;
                                    for (var d = 0; d < disabledElements.length; d++) {
                                        if (disabledElements[d].element === cand) {
                                            isAlreadyDisabled = true;
                                            break;
                                        }
                                    }
                                    if (isAlreadyDisabled) continue;
                                    var candRect = cand.getBoundingClientRect();
                                    if (x >= candRect.left && x <= candRect.right && y >= candRect.top && y <= candRect.bottom) {
                                        var area = candRect.width * candRect.height;
                                        if (area > 0) {
                                            candidates.push({ element: cand, area: area });
                                        }
                                    }
                                }
                                if (candidates.length > 0) {
                                    candidates.sort(function(a, b) { return a.area - b.area; });
                                    nextEl = candidates[0].element;
                                }
                            }

                            if (nextEl === el || !nextEl) {
                                break;
                            }
                            el = nextEl;
                            depth++;
                        } else {
                            break;
                        }
                    }
                    while (el && el.shadowRoot) {
                        try {
                            if (typeof el.shadowRoot.elementFromPoint !== 'function') break;
                            var inner = el.shadowRoot.elementFromPoint(x, y);
                            if (!inner || inner === el) break;
                            el = inner;
                        } catch(e) {
                            break;
                        }
                    }
                    return el;
                } catch(ex) {
                    return null;
                } finally {
                    for (var i = 0; i < disabledElements.length; i++) {
                        if (disabledElements[i].val) {
                            disabledElements[i].element.style.setProperty('pointer-events', disabledElements[i].val, 'important');
                        } else {
                            disabledElements[i].element.style.removeProperty('pointer-events');
                        }
                    }
                    if (deadStyle) deadStyle.disabled = false;
                    overlay.style.setProperty('pointer-events', 'auto', 'important');
                }
            }

            function mousemoveHandler(e) {
                if (e.target && isToolbarChild(e.target)) { hidePreview(); return; }
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();

                var el = getPointElement(e.clientX, e.clientY);
                if (isToolbarChild(el)) { hidePreview(); return; }
                showPreview(el);
            }

            function clickHandler(e) {
                console.log('YuePicker: clickHandler fired target=' + (e.target ? e.target.tagName : 'null'));
                if (e.target && isToolbarChild(e.target)) return;
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();

                var el = getPointElement(e.clientX, e.clientY);
                if (isToolbarChild(el)) return;
                try {
                    toggleElement(el);
                } catch(ex) {}
            }

             // Touch event handlers
            function touchStartHandler(e) {
                if (e.target && isToolbarChild(e.target)) return;
                console.log('YuePicker: touchStartHandler fired');
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();

                var touch = e.touches[0];
                var el = getPointElement(touch.clientX, touch.clientY);
                console.log('YuePicker: touchStartHandler el=' + (el ? el.tagName + ' id=' + el.id + ' class=' + el.className : 'null'));

                if (isToolbarChild(el)) { hidePreview(); lastTouchTarget = null; return; }
                if (isSelected(el)) { hidePreview(); lastTouchTarget = el; return; }
                showPreview(el);
                lastTouchTarget = el;
            }

            function touchMoveHandler(e) {
                if (e.target && isToolbarChild(e.target)) return;
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();
                if (lastTouchTarget === null) return;

                var touch = e.touches[0];
                var el = getPointElement(touch.clientX, touch.clientY);

                if (el === lastTouchTarget || isToolbarChild(el)) return;
                showPreview(el);
                lastTouchTarget = el;
            }

            function touchEndHandler(e) {
                if (e.target && isToolbarChild(e.target)) return;
                console.log('YuePicker: touchEndHandler target=' + (lastTouchTarget ? lastTouchTarget.tagName + ' id=' + lastTouchTarget.id : 'null'));
                e.preventDefault();
                e.stopPropagation();
                if (e.stopImmediatePropagation) e.stopImmediatePropagation();

                var target = lastTouchTarget;
                lastTouchTarget = null;
                if (!target || target === document.body || target === document.documentElement || isToolbarChild(target)) {
                    hidePreview();
                    return;
                }
                try {
                    toggleElement(target);
                } catch(ex) {}
                hidePreview();
            }

            function stop() {
                if (reorderInterval) { clearInterval(reorderInterval); reorderInterval = null; }
                window.__yue_picker_handlers__ = null;
                // Remove document capture handlers via hooked removeEventListener
                document.removeEventListener('mousemove', mousemoveHandler, true);
                document.removeEventListener('click', clickHandler, true);
                document.removeEventListener('touchstart', touchStartHandler, true);
                document.removeEventListener('touchmove', touchMoveHandler, true);
                document.removeEventListener('touchend', touchEndHandler, true);
                if (overlay) {
                    overlay.removeEventListener('mousemove', mousemoveHandler);
                    overlay.removeEventListener('click', clickHandler);
                    overlay.removeEventListener('touchstart', touchStartHandler);
                    overlay.removeEventListener('touchmove', touchMoveHandler);
                    overlay.removeEventListener('touchend', touchEndHandler);
                    overlay.remove();
                    overlay = null;
                }
                if (toolbar) { toolbar.remove(); toolbar = null; }
                for (var i = 0; i < selectionOverlays.length; i++) { selectionOverlays[i].remove(); }
                selectionOverlays = [];
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                selected = [];
                var style = document.getElementById('__yue_picker_style__');
                if (style) { style.remove(); }
                var ost = document.getElementById('__yue_picker_overlay_style__');
                if (ost) { ost.remove(); }
                if (deadStyle) { deadStyle.remove(); deadStyle = null; }
                window.__yuePicker__ = null;
                window.__yuePickerActive__ = false;
            }

            window.__yuePicker__ = { stop: stop, picked: false };
            window.__yuePickerActive__ = true;

            // Tag handlers BEFORE attaching them to the overlay,
            // so the hook (if it intercepts) recognizes them as picker handlers.
            mousemoveHandler.__yue_picker_handler__ = true;
            clickHandler.__yue_picker_handler__ = true;
            touchStartHandler.__yue_picker_handler__ = true;
            touchMoveHandler.__yue_picker_handler__ = true;
            touchEndHandler.__yue_picker_handler__ = true;
            window.__yue_picker_handlers__ = [mousemoveHandler, clickHandler, touchStartHandler, touchMoveHandler, touchEndHandler];

            buildOverlay();
            buildToolbar();
            updateUI();

            reorderInterval = setInterval(function() {
                if (overlay && overlay.parentNode) {
                    if (overlay.nextSibling) {
                        overlay.parentNode.appendChild(overlay);
                    }
                }
                if (toolbar && toolbar.parentNode) {
                    if (toolbar.nextSibling) {
                        toolbar.parentNode.appendChild(toolbar);
                    }
                }
            }, 100);
        })();
        """.trimIndent()
        .replace("BG_COLOR", C_bgColor)
        .replace("BORDER_COLOR", C_borderColor)
        .replace("COUNT_COLOR", C_countColor)
        .replace("BG_BTN", C_btnBg)
        .replace("BTN_TEXT", C_btnText)
        .replace("HAPUS_OPACITY", C_hapusOpacity)
        .replace("CLOSE_COLOR", C_closeColor)
        .replace("HOVER_BG", C_hoverBg)
        .replace("HINT_COLOR", C_hintColor)
    }
}
