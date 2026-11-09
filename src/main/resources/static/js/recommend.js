/*
 * 推荐页防重复提交：第一次提交后禁用按钮，避免同一批课程被重复提交，
 * 第二次点击时服务器会全部返回“已经选过该课程”。
 */
(function () {
    'use strict';

    var form = document.getElementById('recommend-form');
    if (!form) {
        return;
    }

    form.addEventListener('submit', function () {
        var button = form.querySelector('button[type="submit"]');
        if (!button) {
            return;
        }
        button.disabled = true;
        button.textContent = '正在提交…';
    });
})();
