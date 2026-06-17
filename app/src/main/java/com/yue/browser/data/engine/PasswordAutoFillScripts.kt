package com.yue.browser.data.engine

object PasswordAutoFillScripts {

    val detectionScript = """
        (function() {
            try {
                if (window.__yue_password_detected__) return;
                window.__yue_password_detected__ = true;

                var result = detectFormFields();
                if (result) {
                    window.__yue_password_data__ = result;
                }

                function detectFormFields() {
                    var userField = null;
                    var pwdField = null;
                    var inputs = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="file"])');
                    for (var i = 0; i < inputs.length; i++) {
                        var inp = inputs[i];
                        var t = (inp.type || '').toLowerCase();
                        var n = (inp.name || '').toLowerCase();
                        var id = (inp.id || '').toLowerCase();
                        var ac = (inp.getAttribute('autocomplete') || '').toLowerCase();
                        var pl = (inp.placeholder || '').toLowerCase();
                        if (t === 'password') { if (!pwdField) pwdField = inp; }
                        else if (!userField && (t === 'email' || t === 'text' || t === 'tel')) {
                            if (ac === 'username' || ac === 'email' || n.indexOf('email') !== -1 || n.indexOf('user') !== -1 || n.indexOf('login') !== -1 || id.indexOf('email') !== -1 || id.indexOf('user') !== -1 || id.indexOf('login') !== -1 || pl.indexOf('email') !== -1 || pl.indexOf('user') !== -1) userField = inp;
                        }
                    }
                    if (!userField && pwdField) {
                        var ai = document.querySelectorAll('input');
                        for (var j = 0; j < ai.length; j++) { if (ai[j] === pwdField) break; var tt = (ai[j].type || '').toLowerCase(); if (tt === 'text' || tt === 'email' || tt === 'tel') { userField = ai[j]; break; } }
                    }
                    if (pwdField) return { userField: userField, pwdField: pwdField };
                    return null;
                }
            } catch(e) {}
        })();
    """.trimIndent()

    fun getAutofillPromptScript(
        username: String,
        password: String,
        siteName: String,
        isDark: Boolean = false,
        accentColor: String = "#EC4899",
        labelSavedPassword: String = "Saved password",
        labelNotNow: String = "Not now",
        labelFill: String = "Fill"
    ): String {
        val safeUser = username.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val safePass = password.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val safeSite = siteName.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function() {
                try {
                    if (window.__yue_autofill_ready__) return;
                    window.__yue_autofill_ready__ = true;

                    function isUserField(inp) {
                        var t = (inp.type || '').toLowerCase();
                        if (t === 'email') return true;
                        if (t !== 'text' && t !== 'tel') return false;
                        var n = (inp.name || '').toLowerCase();
                        var id = (inp.id || '').toLowerCase();
                        var ac = (inp.getAttribute('autocomplete') || '').toLowerCase();
                        var pl = (inp.placeholder || '').toLowerCase();
                        return ac === 'username' || ac === 'email' ||
                            n.indexOf('email') !== -1 || n.indexOf('user') !== -1 || n.indexOf('login') !== -1 ||
                            id.indexOf('email') !== -1 || id.indexOf('user') !== -1 || id.indexOf('login') !== -1 ||
                            pl.indexOf('email') !== -1 || pl.indexOf('user') !== -1;
                    }

                    function findUserBefore(pwd) {
                        var ai = document.querySelectorAll('input');
                        for (var j = 0; j < ai.length; j++) {
                            if (ai[j] === pwd) break;
                            var tt = (ai[j].type || '').toLowerCase();
                            if (tt === 'text' || tt === 'email' || tt === 'tel') {
                                if (isUserField(ai[j])) return ai[j];
                            }
                        }
                        for (var j = 0; j < ai.length; j++) {
                            if (ai[j] === pwd) break;
                            var tt = (ai[j].type || '').toLowerCase();
                            if (tt === 'text' || tt === 'email' || tt === 'tel') return ai[j];
                        }
                        return null;
                    }

                    function showCard(field) {
                        if (window.__yue_autofill_shown__) return;
                        window.__yue_autofill_shown__ = true;

                        var rect = field.getBoundingClientRect();
                        var vh = window.innerHeight;
                        var cardH = 160;
                        var top, arrowTop;

                        if (rect.bottom + cardH + 20 < vh) {
                            top = rect.bottom + 6;
                            arrowTop = '-7px';
                        } else {
                            top = rect.top - cardH - 10;
                            arrowTop = (cardH - 5) + 'px';
                        }

                        if (top < 4) top = 4;
                        var left = Math.max(4, Math.min(rect.left, window.innerWidth - 330));

                        var dark = $isDark;
                        var accent = '$accentColor';
                        var bg = dark ? '#121212' : '#FFFFFF';
                        var border = dark ? '#2a2a2a' : '#e0e0e0';
                        var headerBg = dark ? '#1a1a1c' : '#F0F1F2';
                        var textColor = dark ? '#E3E3E3' : '#191C1D';
                        var mutedColor = dark ? '#9AA0A6' : '#4D6172';
                        var iconBg = dark ? '#2a2a2a' : '#f0f0f0';

                        var keySvg = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="' + accent + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="12" r="5"/><line x1="13" y1="12" x2="20" y2="12"/><line x1="17" y1="10" x2="17" y2="14"/></svg>';
                        var userSvg = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="' + accent + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 22c0-4 3.6-7 8-7s8 3 8 7"/></svg>';

                        var card = document.createElement('div');
                        card.id = '__yue_autofill_card';
                        card.style.cssText = 'position:fixed;left:' + left + 'px;top:' + top + 'px;z-index:2147483647;font-family:-apple-system,BlinkMacSystemFont,sans-serif;';
                        card.innerHTML = [
                            '<div style="position:relative;background:' + bg + ';border:1px solid ' + border + ';border-radius:12px;box-shadow:0 4px 20px rgba(0,0,0,0.2);padding:0;width:320px;overflow:visible;">',
                            '<div style="position:absolute;top:' + arrowTop + ';left:24px;width:12px;height:12px;background:' + bg + ';border-left:1px solid ' + border + ';border-top:1px solid ' + border + ';transform:rotate(45deg);"></div>',
                            '<div style="background:' + headerBg + ';padding:12px 16px;border-radius:12px 12px 0 0;border-bottom:1px solid ' + border + ';">',
                            '<div style="display:flex;align-items:center;gap:10px;">',
                            '<div style="width:28px;height:28px;background:' + iconBg + ';border-radius:6px;display:flex;align-items:center;justify-content:center;">' + keySvg + '</div>',
                            '<div style="flex:1;min-width:0;">',
                            '<div style="font-size:13px;font-weight:600;color:' + textColor + ';white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">$safeSite</div>',
                            '<div style="font-size:11px;color:' + mutedColor + ';white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' + window.location.hostname + '</div>',
                            '</div>',
                            '<button id="__yue_close_btn" style="background:none;border:none;cursor:pointer;font-size:16px;color:' + mutedColor + ';padding:2px;line-height:1;">✕</button>',
                            '</div>',
                            '</div>',
                            '<div style="padding:10px 16px;display:flex;align-items:center;gap:10px;">',
                            '<div style="width:32px;height:32px;background:' + iconBg + ';border-radius:50%;display:flex;align-items:center;justify-content:center;">' + userSvg + '</div>',
                            '<div style="flex:1;min-width:0;">',
                            '<div style="font-size:13px;color:' + textColor + ';white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">$safeUser</div>',
                            '<div style="font-size:11px;color:' + mutedColor + ';">$labelSavedPassword</div>',
                            '</div>',
                            '</div>',
                            '<div style="padding:6px 12px 10px;display:flex;justify-content:flex-end;gap:6px;border-top:1px solid ' + border + ';">',
                            '<button id="__yue_notnow_btn" style="background:none;border:1px solid ' + border + ';cursor:pointer;font-size:12px;color:' + mutedColor + ';padding:6px 14px;border-radius:8px;font-weight:500;">$labelNotNow</button>',
                            '<button id="__yue_fill_btn" style="background:' + accent + ';border:none;cursor:pointer;font-size:12px;color:#fff;padding:6px 20px;border-radius:8px;font-weight:500;">$labelFill</button>',
                            '</div>',
                            '</div>'
                        ].join('');
                        document.body.appendChild(card);

                        document.getElementById('__yue_fill_btn').onclick = function() {
                            var pwdField = null;
                            var userField = null;
                            var allF = document.querySelectorAll('input');
                            for (var k = 0; k < allF.length; k++) {
                                var ft = (allF[k].type || '').toLowerCase();
                                if (ft === 'password') { if (!pwdField) pwdField = allF[k]; }
                                else if (!userField && (ft === 'email' || ft === 'text' || ft === 'tel') && isUserField(allF[k])) { userField = allF[k]; }
                            }
                            if (!userField && pwdField) userField = findUserBefore(pwdField);
                            if (userField && '$safeUser'.length > 0) setNativeValue(userField, '$safeUser');
                            if (pwdField) setNativeValue(pwdField, '$safePass');
                            window.__yue_autofill_shown__ = false;
                            card.remove();
                        };
                        document.getElementById('__yue_notnow_btn').onclick = function() { window.__yue_autofill_shown__ = false; card.remove(); };
                        document.getElementById('__yue_close_btn').onclick = function() { window.__yue_autofill_shown__ = false; card.remove(); };

                        document.getElementById('__yue_fill_btn').addEventListener('mousedown', function(e) { e.preventDefault(); });

                        setTimeout(function() {
                            document.addEventListener('click', function outsideClick(e) {
                                var c = document.getElementById('__yue_autofill_card');
                                if (!c) { document.removeEventListener('click', outsideClick, true); return; }
                                if (!c.contains(e.target)) {
                                    window.__yue_autofill_shown__ = false;
                                    c.remove();
                                    document.removeEventListener('click', outsideClick, true);
                                }
                            }, true);
                        }, 100);
                    }

                    function tryShow(field) {
                        if (window.__yue_autofill_shown__) return;
                        var t = (field.type || '').toLowerCase();
                        if (t === 'password' || (t === 'email') || isUserField(field)) {
                            showCard(field);
                        }
                    }

                    function setNativeValue(input, value) {
                        var proto = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (proto && proto.set) {
                            proto.set.call(input, value);
                        } else {
                            input.value = value;
                        }
                        input.dispatchEvent(new Event('input', {bubbles:true}));
                        input.dispatchEvent(new Event('change', {bubbles:true}));
                        input.dispatchEvent(new Event('blur', {bubbles:true}));
                        if (input.oninput) try { input.oninput({target:input}); } catch(e) {}
                        if (input.onchange) try { input.onchange({target:input}); } catch(e) {}
                    }

                    document.addEventListener('focusin', function(e) {
                        var tag = e.target.tagName;
                        if (tag === 'INPUT' || tag === 'TEXTAREA') tryShow(e.target);
                    }, true);
                } catch(e) {}
            })();
        """.trimIndent()
    }

    fun getFillScript(username: String, password: String): String {
        val safeUser = username.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val safePass = password.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function() {
                try {
                    function setVal(inp, val) {
                        var p = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (p && p.set) { p.set.call(inp, val); } else { inp.value = val; }
                        inp.dispatchEvent(new Event('input', {bubbles:true}));
                        inp.dispatchEvent(new Event('change', {bubbles:true}));
                    }
                    var pwdField = null;
                    var userField = null;
                    var allInputs = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="file"])');
                    for (var i = 0; i < allInputs.length; i++) {
                        var inp = allInputs[i];
                        var t = (inp.type || '').toLowerCase();
                        if (t === 'password') { if (!pwdField) pwdField = inp; }
                        else if (!userField && (t === 'text' || t === 'email' || t === 'tel')) {
                            var n = (inp.name || '').toLowerCase();
                            var id = (inp.id || '').toLowerCase();
                            var ac = (inp.getAttribute('autocomplete') || '').toLowerCase();
                            var pl = (inp.placeholder || '').toLowerCase();
                            if (ac === 'username' || ac === 'email' || n.indexOf('email') !== -1 || n.indexOf('user') !== -1 || n.indexOf('login') !== -1 || id.indexOf('email') !== -1 || id.indexOf('user') !== -1 || id.indexOf('login') !== -1 || pl.indexOf('email') !== -1 || pl.indexOf('user') !== -1) userField = inp;
                        }
                    }
                    if (!pwdField) return;
                    if (userField && '$safeUser'.length > 0) setVal(userField, '$safeUser');
                    setVal(pwdField, '$safePass');
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
