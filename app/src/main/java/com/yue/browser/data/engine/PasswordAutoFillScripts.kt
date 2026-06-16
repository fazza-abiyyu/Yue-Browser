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
                    if (window.YuePasswordDetect) {
                        window.YuePasswordDetect.onFormDetected(JSON.stringify(result));
                    }
                }

                function detectFormFields() {
                    var usernameField = null;
                    var passwordField = null;

                    var inputs = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="file"])');
                    for (var i = 0; i < inputs.length; i++) {
                        var input = inputs[i];
                        var type = (input.type || '').toLowerCase();
                        var name = (input.name || '').toLowerCase();
                        var id = (input.id || '').toLowerCase();
                        var autocomplete = (input.getAttribute('autocomplete') || '').toLowerCase();
                        var placeholder = (input.placeholder || '').toLowerCase();

                        if (type === 'password') {
                            if (!passwordField) passwordField = input;
                        } else if (type === 'email') {
                            if (!usernameField) usernameField = input;
                        } else if (type === 'text' || type === 'tel') {
                            if (autocomplete === 'username' || autocomplete === 'email' ||
                                name.indexOf('email') !== -1 || name.indexOf('user') !== -1 ||
                                name.indexOf('login') !== -1 || name.indexOf('account') !== -1 ||
                                id.indexOf('email') !== -1 || id.indexOf('user') !== -1 ||
                                id.indexOf('login') !== -1 || id.indexOf('account') !== -1 ||
                                placeholder.indexOf('email') !== -1 || placeholder.indexOf('user') !== -1 ||
                                placeholder.indexOf('login') !== -1) {
                                if (!usernameField) usernameField = input;
                            }
                        }
                    }

                    if (!usernameField && passwordField) {
                        var allInputs = document.querySelectorAll('input');
                        for (var j = 0; j < allInputs.length; j++) {
                            if (allInputs[j] === passwordField) break;
                            var t = (allInputs[j].type || '').toLowerCase();
                            if (t === 'text' || t === 'email' || t === 'tel' || t === 'number') {
                                usernameField = allInputs[j];
                                break;
                            }
                        }
                    }

                    if (passwordField) {
                        return {
                            hasUsername: !!usernameField,
                            usernameSelector: usernameField ? getSelector(usernameField) : '',
                            passwordSelector: getSelector(passwordField),
                            usernameValue: usernameField ? usernameField.value : '',
                            passwordValue: passwordField.value
                        };
                    }
                    return null;
                }

                function getSelector(el) {
                    if (el.id) return '#' + CSS.escape(el.id);
                    if (el.name) return 'input[name="' + el.name.replace(/"/g, '\\"') + '"]';
                    var path = [];
                    while (el && el.nodeType === 1) {
                        var sel = el.tagName.toLowerCase();
                        if (el.id) {
                            path.unshift('#' + CSS.escape(el.id));
                            break;
                        }
                        if (el.className && typeof el.className === 'string') {
                            var classes = el.className.trim().split(/\s+/).slice(0, 2);
                            for (var c = 0; c < classes.length; c++) {
                                sel += '.' + CSS.escape(classes[c]);
                            }
                        }
                        var sibling = el;
                        var nth = 1;
                        while (sibling = sibling.previousElementSibling) {
                            if (sibling.tagName === el.tagName) nth++;
                        }
                        sel += ':nth-of-type(' + nth + ')';
                        path.unshift(sel);
                        el = el.parentElement;
                    }
                    return path.join(' > ');
                }

                function attachSubmitListener() {
                    var forms = document.querySelectorAll('form');
                    for (var f = 0; f < forms.length; f++) {
                        if (forms[f].dataset.yuePasswordHooked) continue;
                        forms[f].dataset.yuePasswordHooked = 'true';
                        forms[f].addEventListener('submit', function(e) {
                            setTimeout(function() {
                                var data = detectFormFields();
                                if (data && data.passwordValue && data.passwordValue.length > 0) {
                                    if (window.YuePasswordDetect) {
                                        window.YuePasswordDetect.onFormSubmitted(JSON.stringify(data));
                                    }
                                }
                            }, 100);
                        });
                    }
                }

                attachSubmitListener();
                setInterval(attachSubmitListener, 2000);

                setTimeout(function() {
                    var data = detectFormFields();
                    if (data) {
                        window.__yue_password_data__ = data;
                        if (window.YuePasswordDetect) {
                            window.YuePasswordDetect.onFormDetected(JSON.stringify(data));
                        }
                    }
                }, 500);
            } catch(e) {}
        })();
    """.trimIndent()

    fun getFillScript(username: String, password: String): String {
        val safeUser = username.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        val safePass = password.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function() {
                try {
                    var data = window.__yue_password_data__;
                    if (!data) {
                        window.__yue_password_data__ = {
                            hasUsername: true,
                            usernameSelector: '',
                            passwordSelector: '',
                            usernameValue: '',
                            passwordValue: ''
                        };
                        data = window.__yue_password_data__;
                        var inputs = document.querySelectorAll('input[type="password"]');
                        if (inputs.length > 0) {
                            data.passwordSelector = (function(el) {
                                if (el.id) return '#' + CSS.escape(el.id);
                                if (el.name) return 'input[name="' + el.name.replace(/"/g, '\\"') + '"]';
                                return null;
                            })(inputs[0]);
                            if (data.passwordSelector) {
                                data.hasUsername = true;
                                var before = inputs[0].previousElementSibling;
                                var parent = inputs[0].parentElement;
                                var textInputs = parent ? parent.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"]') : [];
                                if (textInputs.length > 0) {
                                    data.usernameSelector = (function(el) {
                                        if (el.id) return '#' + CSS.escape(el.id);
                                        if (el.name) return 'input[name="' + el.name.replace(/"/g, '\\"') + '"]';
                                        return null;
                                    })(textInputs[0]);
                                }
                            }
                        }
                    }

                    if (!data || !data.passwordSelector) return;

                    if (data.hasUsername && data.usernameSelector && '$safeUser'.length > 0) {
                        var uField = document.querySelector(data.usernameSelector);
                        if (uField) {
                            uField.value = '$safeUser';
                            uField.dispatchEvent(new Event('input', {bubbles: true}));
                            uField.dispatchEvent(new Event('change', {bubbles: true}));
                            uField.dispatchEvent(new Event('blur', {bubbles: true}));
                        }
                    }

                    if (data.passwordSelector) {
                        var pField = document.querySelector(data.passwordSelector);
                        if (pField) {
                            pField.value = '$safePass';
                            pField.dispatchEvent(new Event('input', {bubbles: true}));
                            pField.dispatchEvent(new Event('change', {bubbles: true}));
                            pField.dispatchEvent(new Event('blur', {bubbles: true}));
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    fun getSavePromptScript(): String {
        return """
            (function() {
                try {
                    var data = window.__yue_password_data__;
                    if (data && data.passwordValue && data.passwordValue.length > 0) {
                        if (window.YuePasswordDetect) {
                            window.YuePasswordDetect.onFormSubmitted(JSON.stringify(data));
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
