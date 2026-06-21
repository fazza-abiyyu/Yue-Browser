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
            if (window.__yuePicker__) { window.__yuePicker__.stop(); }
            console.log('YuePicker: init, window.YuePicker=' + (typeof window.YuePicker));

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

                document.body.appendChild(toolbar);
            }

            function submitSelection() {
                if (selected.length === 0) { console.log('YuePicker: submit - empty selection'); return; }
                console.log('YuePicker: submitSelection count=' + selected.length);
                var selectors;
                try {
                    selectors = [];
                    for (var i = 0; i < selected.length; i++) {
                        var s = getCssSelector(selected[i]);
                        console.log('YuePicker: sel[' + i + ']=' + s);
                        selectors.push(s);
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
                        selectionOverlays.push(buildOverlay(selected[i], '#EC4899', 'rgba(236,72,153,0.15)'));
                    }
                }
            }

            function showPreview(el) {
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                if (!el || el === document.body || el === document.documentElement || isToolbarChild(el)) return;
                if (isSelected(el)) return;
                previewOverlay = buildOverlay(el, '#00B4D8', 'rgba(0,180,216,0.08)');
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
                e.stopPropagation();
                if (isSelected(el)) { hidePreview(); lastTouchTarget = el; return; }
                showPreview(el);
                lastTouchTarget = el;
            }

            function touchMoveHandler(e) {
                if (lastTouchTarget === null) return;
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
                    e.stopPropagation();
                    window.__yuePicker__.touchActive = true;
                    toggleElement(target);
                } catch(ex) {}
                hidePreview();
            }

            function stop() {
                document.removeEventListener('mousemove', mousemoveHandler, true);
                document.removeEventListener('click', clickHandler, true);
                document.removeEventListener('touchstart', touchStartHandler, true);
                document.removeEventListener('touchmove', touchMoveHandler, true);
                document.removeEventListener('touchend', touchEndHandler, true);
                if (toolbar) { toolbar.remove(); toolbar = null; }
                for (var i = 0; i < selectionOverlays.length; i++) { selectionOverlays[i].remove(); }
                selectionOverlays = [];
                if (previewOverlay) { previewOverlay.remove(); previewOverlay = null; }
                selected = [];
                var style = document.getElementById('__yue_picker_style__');
                if (style) { style.remove(); }
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
