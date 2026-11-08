/*
 * 输入即搜索：
 * - 文本输入防抖 350ms 自动提交
 * - 兼容中文输入法组合输入，不在拼音阶段触发
 * - 筛选复选框变化后立即提交
 * - 搜索后回到页面时恢复输入框焦点和光标
 */
(function () {
    'use strict';

    var form = document.querySelector('form.auto-search');
    if (!form) {
        return;
    }

    var inputs = Array.prototype.slice.call(
            form.querySelectorAll('input[type="search"], input[type="text"], input[type="checkbox"]'));
    if (!inputs.length) {
        return;
    }

    var timer = null;
    var composing = false;

    function submitSoon(delay, keepSearchFocus) {
        if (timer) {
            clearTimeout(timer);
        }
        if (keepSearchFocus) {
            rememberFocus();
        }
        timer = setTimeout(function () {
            if (composing) {
                return;
            }
            if (typeof form.requestSubmit === 'function') {
                form.requestSubmit();
            } else {
                form.submit();
            }
        }, delay);
    }

    function rememberFocus() {
        try {
            sessionStorage.setItem('autoSearchFocus', '1');
        } catch (e) {
            // 隐私模式下 sessionStorage 可能不可用
        }
    }

    function restoreFocus(searchInput) {
        var shouldRestore = false;
        try {
            shouldRestore = sessionStorage.getItem('autoSearchFocus') === '1';
            sessionStorage.removeItem('autoSearchFocus');
        } catch (e) {
            shouldRestore = false;
        }
        if (!shouldRestore) {
            return;
        }
        searchInput.focus();
        try {
            searchInput.setSelectionRange(searchInput.value.length, searchInput.value.length);
        } catch (e) {
            // 某些输入类型不支持 setSelectionRange
        }
    }

    var searchInput = null;
    inputs.forEach(function (input) {
        if (input.type === 'checkbox') {
            input.addEventListener('change', function () {
                submitSoon(0, false);
            });
            return;
        }
        if (!searchInput) {
            searchInput = input;
        }
        input.addEventListener('compositionstart', function () {
            composing = true;
        });
        input.addEventListener('compositionend', function () {
            composing = false;
            submitSoon(120, true);
        });
        input.addEventListener('input', function () {
            if (!composing) {
                submitSoon(350, true);
            }
        });
    });

    if (searchInput) {
        restoreFocus(searchInput);
    }
})();
