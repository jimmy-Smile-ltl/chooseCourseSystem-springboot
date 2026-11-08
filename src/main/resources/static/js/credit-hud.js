/*
 * 学分星环 HUD：
 * - 右下角悬浮 Canvas，显示累计学分 / 上限
 * - 金色刻度标记目标学分，达标后星环变为金色
 * - 监听 course-planner 发出的 credit-total-change 事件
 */
(function () {
    'use strict';

    var canvas = document.getElementById('credit-hud');
    if (!canvas) {
        return;
    }

    var maxCredits = parseInt(canvas.getAttribute('data-max') || '30', 10);
    var goal = parseInt(canvas.getAttribute('data-goal') || '20', 10);
    var initial = parseInt(canvas.getAttribute('data-selected') || '0', 10);
    if (!isFinite(maxCredits) || maxCredits <= 0) {
        maxCredits = 30;
    }
    if (!isFinite(goal) || goal <= 0) {
        goal = 20;
    }
    if (!isFinite(initial) || initial < 0) {
        initial = 0;
    }

    var ctx = canvas.getContext('2d');
    if (!ctx) {
        return;
    }

    var reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var size = 140;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(size * dpr);
    canvas.height = Math.round(size * dpr);
    canvas.style.width = size + 'px';
    canvas.style.height = size + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    var current = reduceMotion ? initial : 0;
    var target = initial;
    var glowUntil = 0;
    var frame = null;

    function requestDraw() {
        if (!frame) {
            frame = requestAnimationFrame(draw);
        }
    }

    function draw(now) {
        var moving = Math.abs(target - current) > 0.01;
        if (moving && !reduceMotion) {
            current += (target - current) * 0.18;
            if (Math.abs(target - current) < 0.05) {
                current = target;
            }
        } else {
            current = target;
        }

        var reached = current >= goal;
        var cx = 70;
        var cy = 64;
        var radius = 50;
        var start = -Math.PI / 2;
        var ratio = Math.max(0, Math.min(1, current / maxCredits));

        ctx.clearRect(0, 0, size, size);

        // 玻璃圆盘
        var bg = ctx.createRadialGradient(cx, cy - 12, 8, cx, cy, 66);
        bg.addColorStop(0, 'rgba(30, 41, 59, .94)');
        bg.addColorStop(1, 'rgba(15, 23, 42, .90)');
        ctx.beginPath();
        ctx.arc(cx, cy, 64, 0, Math.PI * 2);
        ctx.fillStyle = bg;
        ctx.fill();
        ctx.strokeStyle = reached ? 'rgba(251, 191, 36, .55)' : 'rgba(148, 163, 184, .35)';
        ctx.lineWidth = 1;
        ctx.stroke();

        // 学分刻度
        var ticks = Math.max(1, Math.min(30, maxCredits));
        for (var i = 0; i < ticks; i++) {
            var angle = start + (Math.PI * 2 * i) / maxCredits;
            var longTick = i % 5 === 0;
            var inner = radius + (longTick ? 6 : 9);
            var outer = radius + 13;
            ctx.beginPath();
            ctx.moveTo(cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner);
            ctx.lineTo(cx + Math.cos(angle) * outer, cy + Math.sin(angle) * outer);
            ctx.strokeStyle = longTick
                    ? 'rgba(148, 163, 184, .55)'
                    : 'rgba(148, 163, 184, .22)';
            ctx.lineWidth = longTick ? 1.5 : 1;
            ctx.stroke();
        }

        // 进度弧
        var arcGradient = ctx.createLinearGradient(20, 110, 120, 20);
        if (reached) {
            arcGradient.addColorStop(0, '#f59e0b');
            arcGradient.addColorStop(0.5, '#fbbf24');
            arcGradient.addColorStop(1, '#fde68a');
        } else {
            arcGradient.addColorStop(0, '#22d3ee');
            arcGradient.addColorStop(0.5, '#38bdf8');
            arcGradient.addColorStop(1, '#818cf8');
        }
        ctx.beginPath();
        ctx.arc(cx, cy, radius, start, start + Math.PI * 2 * ratio);
        ctx.strokeStyle = arcGradient;
        ctx.lineWidth = 7;
        ctx.lineCap = 'round';
        if (now < glowUntil) {
            ctx.shadowColor = reached ? '#fbbf24' : '#38bdf8';
            ctx.shadowBlur = 14 + Math.sin(now / 90) * 6;
        }
        if (ratio > 0) {
            ctx.stroke();
        }
        ctx.shadowBlur = 0;

        // 目标刻度
        var goalAngle = start + (Math.PI * 2 * Math.min(goal, maxCredits)) / maxCredits;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(goalAngle) * (radius - 8),
                cy + Math.sin(goalAngle) * (radius - 8));
        ctx.lineTo(cx + Math.cos(goalAngle) * (radius + 10),
                cy + Math.sin(goalAngle) * (radius + 10));
        ctx.strokeStyle = '#fbbf24';
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx + Math.cos(goalAngle) * (radius + 12),
                cy + Math.sin(goalAngle) * (radius + 12), 2.2, 0, Math.PI * 2);
        ctx.fillStyle = '#fde68a';
        ctx.fill();

        // 中央文字
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillStyle = reached ? '#fde68a' : '#f8fafc';
        ctx.font = '700 28px system-ui, -apple-system, "Microsoft YaHei", sans-serif';
        ctx.fillText(String(Math.round(current)), cx, cy - 6);
        ctx.fillStyle = 'rgba(148, 163, 184, .95)';
        ctx.font = '500 11px system-ui, -apple-system, "Microsoft YaHei", sans-serif';
        ctx.fillText('/ ' + maxCredits + ' 学分', cx, cy + 17);
        ctx.fillStyle = reached ? '#fbbf24' : 'rgba(148, 163, 184, .85)';
        ctx.font = '500 10px system-ui, -apple-system, "Microsoft YaHei", sans-serif';
        ctx.fillText(reached ? '目标达成' : '目标 ' + goal + ' 学分', cx, cy + 34);

        var needsGlow = now < glowUntil;
        if ((moving && !reduceMotion) || needsGlow) {
            frame = requestAnimationFrame(draw);
        } else {
            frame = null;
        }
    }

    document.addEventListener('credit-total-change', function (event) {
        var total = Number(event.detail && event.detail.total);
        if (!isFinite(total) || total < 0) {
            return;
        }
        if (total >= goal && target < goal) {
            glowUntil = performance.now() + 1200;
        }
        target = total;
        if (reduceMotion) {
            current = total;
            draw(performance.now());
        } else {
            requestDraw();
        }
    });

    requestDraw();
})();
