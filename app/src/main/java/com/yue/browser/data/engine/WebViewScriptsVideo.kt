package com.yue.browser.data.engine

object WebViewScriptsVideo {
    fun elementPickerScript(isDark: Boolean): String {
        val C_bgColor = if (isDark) "#121212" else "#FFFFFF"
        val C_countColor = if (isDark) "#E3E3E3" else "#191C1D"
        val C_btnBg = "#EC4899"
        val C_btnText = "#FFFFFF"
        val C_hapusOpacity = "0.5"
        val C_closeBg = if (isDark) "#1A1A1C" else "#F0F1F2"
        val C_closeColor = if (isDark) "#9AA0A6" else "#4D6172"
        val C_hintColor = if (isDark) "#9AA0A6" else "#4D6172"
        return """
        (function() {
            if (window.__yuePicker__) { window.__yuePicker__.stop(); }

            var selected = [];
            var selectionOverlays = [];
            var previewOverlay = null;
            var lastTouchTarget = null;
            var toolbar = null;

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
                return result;
            }

            function escapeCss(str) {
                if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(str);
                return str.replace(/[!"#$%&'()*+,.\/:;<=>?@[\]^`{|}~ ]/g, '\\$&');
            }

            function getCssSelector(el) {
                if (!el || el === document.body) return 'body';
                if (el.id && !/^[0-9]/.test(el.id)) {
                    return el.tagName.toLowerCase() + '#' + escapeCss(el.id);
                }
                var ownClasses = getClasses(el);
                var tag = el.tagName.toLowerCase();
                if (ownClasses.length > 0) {
                    var clsStr = '';
                    var limit = ownClasses.length > 3 ? 3 : ownClasses.length;
                    for (var i = 0; i < limit; i++) { clsStr += '.' + escapeCss(ownClasses[i]); }
                    var parent = el.parentElement;
                    if (parent && parent !== document.body && parent !== document.documentElement) {
                        var pTag = parent.tagName.toLowerCase();
                        var pId = parent.id && !/^[0-9]/.test(parent.id) ? '#' + escapeCss(parent.id) : '';
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
                toolbar = document.createElement('div');
                toolbar.id = '__yue_picker_toolbar__';
                toolbar.style.cssText = 'position:fixed;bottom:20px;left:50%;transform:translateX(-50%);z-index:2147483647;display:flex;flex-direction:column;align-items:center;gap:6px;background:BG_COLOR;border-radius:12px;padding:10px 18px;box-shadow:0 4px 24px rgba(0,0,0,0.5);font-family:sans-serif;';

                var row = document.createElement('div');
                row.style.cssText = 'display:flex;align-items:center;gap:10px;';

                var count = document.createElement('span');
                count.id = '__yue_picker_count__';
                count.style.cssText = 'color:COUNT_COLOR;font-size:13px;min-width:70px;text-align:center;user-select:none;';
                count.textContent = '0 selected';
                row.appendChild(count);

                var hapus = document.createElement('button');
                hapus.id = '__yue_picker_hapus__';
                hapus.textContent = 'Hapus';
                hapus.style.cssText = 'background:BG_BTN;color:BTN_TEXT;border:none;border-radius:8px;padding:8px 20px;font-size:13px;font-weight:bold;cursor:pointer;opacity:HAPUS_OPACITY;transition:opacity 0.2s;user-select:none;';
                hapus.onclick = function() { submitSelection(); };
                row.appendChild(hapus);

                var close = document.createElement('button');
                close.id = '__yue_picker_close__';
                close.textContent = '\u2715';
                close.style.cssText = 'background:CLOSE_BG;color:CLOSE_COLOR;border:none;border-radius:8px;padding:8px 14px;font-size:16px;cursor:pointer;user-select:none;';
                close.onclick = function() { cancelPicker(); };
                row.appendChild(close);

                toolbar.appendChild(row);

                var hint = document.createElement('div');
                hint.style.cssText = 'color:HINT_COLOR;font-size:11px;text-align:center;user-select:none;';
                hint.textContent = 'Ketuk elemen untuk diblokir';
                toolbar.appendChild(hint);

                document.body.appendChild(toolbar);
            }

            function submitSelection() {
                if (selected.length === 0) return;
                try {
                    var selectors = [];
                    for (var i = 0; i < selected.length; i++) {
                        selectors.push(getCssSelector(selected[i]));
                    }
                    var json = JSON.stringify(selectors);
                    window.__yuePicker__.picked = true;
                    window.__yuePicker__.stop();
                    if (window.YuePicker) {
                        YuePicker.onPickedMultiple(json);
                    }
                } catch(ex) {}
            }

            function cancelPicker() {
                try {
                    if (window.__yuePicker__) {
                        window.__yuePicker__.stop();
                    }
                    if (window.YuePicker && window.YuePicker.onCancelled) {
                        YuePicker.onCancelled();
                    }
                } catch(ex) {}
            }

            function updateUI() {
                var count = document.getElementById('__yue_picker_count__');
                if (count) count.textContent = selected.length + ' selected';
                var hapus = document.getElementById('__yue_picker_hapus__');
                if (hapus) hapus.style.opacity = selected.length > 0 ? '1' : '0.5';
            }

            function buildOverlay(el, color, bgColor) {
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
                        selectionOverlays.push(buildOverlay(selected[i], '#e94560', 'rgba(233,69,96,0.12)'));
                    }
                }
            }

            function showPreview(el) {
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                if (!el || el === document.body || el === document.documentElement || isToolbarChild(el)) return;
                if (isSelected(el)) return;
                previewOverlay = buildOverlay(el, '#4a9eff', 'rgba(74,158,255,0.08)');
            }

            function hidePreview() {
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
            }

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

            function mousemoveHandler(e) {
                if (isToolbarChild(e.target)) { hidePreview(); return; }
                showPreview(e.target);
            }

            function clickHandler(e) {
                if (isToolbarChild(e.target)) return;
                if (window.__yuePicker__.touchActive) {
                    window.__yuePicker__.touchActive = false;
                    e.preventDefault();
                    return;
                }
                try {
                    e.preventDefault();
                    toggleElement(e.target);
                } catch(ex) {}
            }

            function touchStartHandler(e) {
                window.__yuePicker__.touchActive = false;
                var touch = e.touches[0];
                var el = document.elementFromPoint(touch.clientX, touch.clientY);
                if (isToolbarChild(el)) { hidePreview(); lastTouchTarget = null; return; }
                if (isSelected(el)) { hidePreview(); lastTouchTarget = el; return; }
                showPreview(el);
                lastTouchTarget = el;
            }

            function touchMoveHandler(e) {
                var touch = e.touches[0];
                var el = document.elementFromPoint(touch.clientX, touch.clientY);
                if (el === lastTouchTarget || isToolbarChild(el)) return;
                showPreview(el);
                lastTouchTarget = el;
            }

            function touchEndHandler(e) {
                var target = lastTouchTarget;
                lastTouchTarget = null;
                if (!target || target === document.body || target === document.documentElement || isToolbarChild(target)) {
                    hidePreview();
                    return;
                }
                try {
                    e.preventDefault();
                    window.__yuePicker__.touchActive = true;
                    toggleElement(target);
                } catch(ex) {}
                hidePreview();
            }

            function stop() {
                document.removeEventListener('mousemove', mousemoveHandler, true);
                document.removeEventListener('click', clickHandler, true);
                document.removeEventListener('touchstart', touchStartHandler, { capture: true });
                document.removeEventListener('touchmove', touchMoveHandler, { capture: true });
                document.removeEventListener('touchend', touchEndHandler, { capture: true });
                if (toolbar) { toolbar.remove(); toolbar = null; }
                for (var i = 0; i < selectionOverlays.length; i++) { selectionOverlays[i].remove(); }
                selectionOverlays = [];
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                selected = [];
                window.__yuePicker__ = null;
            }

            window.__yuePicker__ = { stop: stop, picked: false, touchActive: false };
            buildToolbar();
            updateUI();

            document.addEventListener('mousemove', mousemoveHandler, true);
            document.addEventListener('click', clickHandler, true);
            document.addEventListener('touchstart', touchStartHandler, { passive: true, capture: true });
            document.addEventListener('touchmove', touchMoveHandler, { passive: true, capture: true });
            document.addEventListener('touchend', touchEndHandler, { passive: false, capture: true });
        })();
        """.trimIndent()
        .replace("BG_COLOR", C_bgColor)
        .replace("COUNT_COLOR", C_countColor)
        .replace("BG_BTN", C_btnBg)
        .replace("BTN_TEXT", C_btnText)
        .replace("HAPUS_OPACITY", C_hapusOpacity)
        .replace("CLOSE_BG", C_closeBg)
        .replace("CLOSE_COLOR", C_closeColor)
        .replace("HINT_COLOR", C_hintColor)
    }
}
