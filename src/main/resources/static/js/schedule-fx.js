/*
 * 课表极光 + 当前节次扫描：
 * - Canvas 位于课表网格内，绘制缓慢漂移的极光
 * - 高亮今天这一列
 * - 当前节次所在行有一条扫描光带，随时间移动
 * - prefers-reduced-motion 下只绘制静态版本
 */
(function () {
    'use strict';

    var grid = document.querySelector('.tt-grid');
    var canvas = document.getElementById('schedule-fx');
    if (!grid || !canvas) {
        return;
    }
    var ctx = canvas.getContext('2d');
    if (!ctx) {
        return;
    }

    var reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var width = 0;
    var height = 0;
    var frame = null;

    var HEADER_HEIGHT = 42;
    var ROW_HEIGHT = 62;
    var LABEL_WIDTH = 82;
    var DAYS = 7;
    // 与 ScheduleService.PERIOD_STARTS 保持一致，单位：分钟
    var PERIOD_STARTS = [510, 565, 620, 675, 780, 835, 890, 945, 1000, 1110, 1165, 1220];
    var LAST_PERIOD_END = 1275;
    var DAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    var PERIOD_LABELS = ['第一节', '第二节', '第三节', '第四节', '第五节', '第六节',
        '第七节', '第八节', '第九节', '第十节', '第十一节', '第十二节'];

    function resize() {
        var nextWidth = grid.clientWidth;
        var nextHeight = grid.clientHeight;
        if (nextWidth === width && nextHeight === height) {
            return;
        }
        width = nextWidth;
        height = nextHeight;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function nowInfo() {
        var now = new Date();
        var minutes = now.getHours() * 60 + now.getMinutes();
        var period = -1;
        for (var i = 0; i < PERIOD_STARTS.length; i++) {
            var end = i + 1 < PERIOD_STARTS.length ? PERIOD_STARTS[i + 1] : LAST_PERIOD_END;
            if (minutes >= PERIOD_STARTS[i] && minutes < end) {
                period = i;
                break;
            }
        }
        return {
            day: now.getDay() === 0 ? 7 : now.getDay(),
            period: period
        };
    }

    function drawBlob(x, y, radius, color, alpha) {
        var gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
        gradient.addColorStop(0, 'rgba(' + color + ', ' + alpha + ')');
        gradient.addColorStop(0.55, 'rgba(' + color + ', ' + alpha * 0.35 + ')');
        gradient.addColorStop(1, 'rgba(' + color + ', 0)');
        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.arc(x, y, radius, 0, Math.PI * 2);
        ctx.fill();
    }

    function drawAurora(seconds) {
        var t = reduceMotion ? 0 : seconds;
        drawBlob(
                width * (0.22 + 0.06 * Math.sin(t * 0.18)),
                height * (0.22 + 0.05 * Math.cos(t * 0.24)),
                width * 0.42,
                '56, 189, 248',
                0.055);
        drawBlob(
                width * (0.62 + 0.07 * Math.cos(t * 0.15)),
                height * (0.58 + 0.06 * Math.sin(t * 0.19)),
                width * 0.46,
                '129, 140, 248',
                0.045);
        drawBlob(
                width * (0.82 + 0.05 * Math.sin(t * 0.12)),
                height * (0.35 + 0.07 * Math.cos(t * 0.21)),
                width * 0.34,
                '244, 114, 182',
                0.032);
    }

    function drawTodayColumn(info, seconds) {
        var dayWidth = (width - LABEL_WIDTH) / DAYS;
        var x = LABEL_WIDTH + (info.day - 1) * dayWidth;
        var pulse = reduceMotion ? 0.5 : 0.5 + 0.5 * Math.sin(seconds * 1.4);

        var fill = ctx.createLinearGradient(0, HEADER_HEIGHT, 0, height);
        fill.addColorStop(0, 'rgba(56, 189, 248, ' + (0.035 + pulse * 0.025) + ')');
        fill.addColorStop(1, 'rgba(56, 189, 248, 0.012)');
        ctx.fillStyle = fill;
        ctx.fillRect(x, HEADER_HEIGHT, dayWidth, height - HEADER_HEIGHT);

        ctx.fillStyle = 'rgba(56, 189, 248, ' + (0.16 + pulse * 0.14) + ')';
        ctx.fillRect(x, HEADER_HEIGHT, 1, height - HEADER_HEIGHT);
        ctx.fillRect(x + dayWidth - 1, HEADER_HEIGHT, 1, height - HEADER_HEIGHT);
    }

    function drawCurrentPeriod(info, seconds) {
        if (info.period < 0) {
            return;
        }
        var t = reduceMotion ? 0 : seconds;
        var y = HEADER_HEIGHT + info.period * ROW_HEIGHT;
        var widthToDraw = width - LABEL_WIDTH;

        var band = ctx.createLinearGradient(LABEL_WIDTH, 0, width, 0);
        band.addColorStop(0, 'rgba(56, 189, 248, .14)');
        band.addColorStop(0.35, 'rgba(129, 140, 248, .07)');
        band.addColorStop(1, 'rgba(56, 189, 248, .025)');
        ctx.fillStyle = band;
        ctx.fillRect(LABEL_WIDTH, y, widthToDraw, ROW_HEIGHT);

        // 扫描光带
        var sweep = LABEL_WIDTH + ((t * 95) % Math.max(1, widthToDraw));
        var sweepWidth = 180;
        var sweepGradient = ctx.createLinearGradient(
                sweep - sweepWidth / 2, 0, sweep + sweepWidth / 2, 0);
        sweepGradient.addColorStop(0, 'rgba(125, 211, 252, 0)');
        sweepGradient.addColorStop(0.5, 'rgba(125, 211, 252, .20)');
        sweepGradient.addColorStop(1, 'rgba(125, 211, 252, 0)');
        ctx.fillStyle = sweepGradient;
        ctx.fillRect(Math.max(LABEL_WIDTH, sweep - sweepWidth / 2), y,
                sweepWidth, ROW_HEIGHT);

        ctx.fillStyle = 'rgba(125, 211, 252, .55)';
        ctx.fillRect(LABEL_WIDTH, y, widthToDraw, 1);
        ctx.fillStyle = 'rgba(125, 211, 252, .22)';
        ctx.fillRect(LABEL_WIDTH, y + ROW_HEIGHT - 1, widthToDraw, 1);

        var glowX = LABEL_WIDTH + 25;
        var glowY = y + ROW_HEIGHT / 2;
        var glow = ctx.createRadialGradient(glowX, glowY, 0, glowX, glowY, 46);
        glow.addColorStop(0, 'rgba(125, 211, 252, .38)');
        glow.addColorStop(1, 'rgba(125, 211, 252, 0)');
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(glowX, glowY, 46, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = 'rgba(224, 242, 254, .95)';
        ctx.beginPath();
        ctx.arc(glowX, glowY, 2.6, 0, Math.PI * 2);
        ctx.fill();
    }

    function render(now) {
        var seconds = now / 1000;
        resize();
        if (!width || !height) {
            frame = requestAnimationFrame(render);
            return;
        }
        ctx.clearRect(0, 0, width, height);
        drawAurora(seconds);
        var info = nowInfo();
        drawTodayColumn(info, seconds);
        drawCurrentPeriod(info, seconds);

        if (!reduceMotion && !document.hidden) {
            frame = requestAnimationFrame(render);
        } else {
            frame = null;
        }
    }

    function refreshNowLabel() {
        var label = document.getElementById('schedule-now');
        if (!label) {
            return;
        }
        var info = nowInfo();
        label.hidden = false;
        label.textContent = info.period >= 0
                ? '· 当前：' + DAY_LABELS[info.day - 1] + ' ' + PERIOD_LABELS[info.period]
                : '· 当前不在上课时段';
    }

    function start() {
        refreshNowLabel();
        if (reduceMotion) {
            render(0);
            return;
        }
        if (!frame && !document.hidden) {
            frame = requestAnimationFrame(render);
        }
    }

    if (window.ResizeObserver) {
        new ResizeObserver(function () {
            resize();
            if (reduceMotion) {
                render(0);
            }
        }).observe(grid);
    } else {
        window.addEventListener('resize', function () {
            resize();
            if (reduceMotion) {
                render(0);
            }
        });
    }

    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) {
            start();
        }
    });

    setInterval(refreshNowLabel, 60000);
    start();
})();
