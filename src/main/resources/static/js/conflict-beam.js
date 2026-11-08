/*
 * 冲突闪电：
 * - 监听 course-planner 发出的 course-conflicts-change
 * - 在冲突的两门课程行之间绘制红色闪电并高亮行
 * - 滚动、缩放时实时跟随；冲突消失后自动销毁 Canvas
 */
(function () {
    'use strict';

    var reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var canvas = null;
    var ctx = null;
    var frame = null;
    var pairs = [];
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var width = 0;
    var height = 0;

    function findRow(cno) {
        var rows = document.querySelectorAll('tr.course-row');
        for (var i = 0; i < rows.length; i++) {
            if (rows[i].getAttribute('data-cno') === cno) {
                return rows[i];
            }
        }
        return null;
    }

    function ensureCanvas() {
        if (canvas) {
            return;
        }
        canvas = document.createElement('canvas');
        canvas.className = 'conflict-beam-canvas';
        canvas.setAttribute('aria-hidden', 'true');
        document.body.appendChild(canvas);
        ctx = canvas.getContext('2d');
        resize();
    }

    function resize() {
        if (!canvas) {
            return;
        }
        width = window.innerWidth;
        height = window.innerHeight;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function stop() {
        if (frame) {
            cancelAnimationFrame(frame);
            frame = null;
        }
        if (canvas && canvas.parentNode) {
            canvas.parentNode.removeChild(canvas);
        }
        canvas = null;
        ctx = null;
    }

    function clearRowHighlights() {
        var rows = document.querySelectorAll('tr.course-row.conflict-source');
        for (var i = 0; i < rows.length; i++) {
            rows[i].classList.remove('conflict-source');
        }
    }

    function highlightRows() {
        clearRowHighlights();
        pairs.forEach(function (pair) {
            var a = findRow(pair.a);
            var b = findRow(pair.b);
            if (a) {
                a.classList.add('conflict-source');
            }
            if (b) {
                b.classList.add('conflict-source');
            }
        });
    }

    function noise(seed) {
        var value = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
        return value - Math.floor(value);
    }

    function drawPair(pair, pairIndex, now) {
        var rowA = findRow(pair.a);
        var rowB = findRow(pair.b);
        if (!rowA || !rowB) {
            return;
        }
        var rectA = rowA.getBoundingClientRect();
        var rectB = rowB.getBoundingClientRect();
        if ((rectA.bottom < 0 && rectB.bottom < 0)
                || (rectA.top > height && rectB.top > height)) {
            return;
        }

        var x = Math.min(width - 24, Math.min(rectA.right, rectB.right) - 70);
        var y1 = Math.max(8, Math.min(height - 8, rectA.top + rectA.height / 2));
        var y2 = Math.max(8, Math.min(height - 8, rectB.top + rectB.height / 2));
        if (Math.abs(y2 - y1) < 6) {
            return;
        }

        var flickerSeed = reduceMotion ? 11 : Math.floor(now / 85) + pairIndex * 31;
        var points = [{x: x, y: y1}];
        var segments = 7;
        for (var i = 1; i < segments; i++) {
            var t = i / segments;
            var offset = (i % 2 === 0 ? 1 : -1)
                    * (8 + noise(flickerSeed + i * 7) * 22);
            points.push({
                x: x + offset * Math.sin(Math.PI * t),
                y: y1 + (y2 - y1) * t
            });
        }
        points.push({x: x, y: y2});

        function tracePath() {
            ctx.beginPath();
            ctx.moveTo(points[0].x, points[0].y);
            for (var p = 1; p < points.length; p++) {
                ctx.lineTo(points[p].x, points[p].y);
            }
        }

        ctx.save();
        ctx.lineJoin = 'round';
        ctx.lineCap = 'round';

        ctx.shadowColor = 'rgba(239, 68, 68, .95)';
        ctx.shadowBlur = 18;
        tracePath();
        ctx.strokeStyle = 'rgba(239, 68, 68, .30)';
        ctx.lineWidth = 11;
        ctx.stroke();

        tracePath();
        ctx.strokeStyle = 'rgba(248, 113, 113, .88)';
        ctx.lineWidth = 3;
        ctx.stroke();

        tracePath();
        ctx.strokeStyle = 'rgba(254, 226, 226, .98)';
        ctx.lineWidth = 1.2;
        ctx.stroke();

        [y1, y2].forEach(function (y) {
            var glow = ctx.createRadialGradient(x, y, 0, x, y, 16);
            glow.addColorStop(0, 'rgba(254, 226, 226, .95)');
            glow.addColorStop(0.35, 'rgba(248, 113, 113, .55)');
            glow.addColorStop(1, 'rgba(239, 68, 68, 0)');
            ctx.fillStyle = glow;
            ctx.beginPath();
            ctx.arc(x, y, 16, 0, Math.PI * 2);
            ctx.fill();
        });

        // 沿闪电随机迸出的小火花
        for (var s = 0; s < 4; s++) {
            var point = points[1 + ((s * 2) % (points.length - 2))];
            var sparkX = point.x + (noise(flickerSeed + s * 13) - 0.5) * 18;
            var sparkY = point.y + (noise(flickerSeed + s * 29) - 0.5) * 22;
            ctx.fillStyle = s % 2
                    ? 'rgba(254, 226, 226, .9)'
                    : 'rgba(251, 191, 36, .9)';
            ctx.beginPath();
            ctx.arc(sparkX, sparkY, 1.2 + noise(flickerSeed + s) * 1.8,
                    0, Math.PI * 2);
            ctx.fill();
        }
        ctx.restore();
    }

    function draw(now) {
        if (!ctx) {
            return;
        }
        if (width !== window.innerWidth || height !== window.innerHeight) {
            resize();
        }
        ctx.clearRect(0, 0, width, height);
        pairs.forEach(function (pair, index) {
            drawPair(pair, index, now);
        });
        frame = requestAnimationFrame(draw);
    }

    document.addEventListener('course-conflicts-change', function (event) {
        pairs = (event.detail && event.detail.pairs) || [];
        highlightRows();
        if (!pairs.length) {
            stop();
            return;
        }
        ensureCanvas();
        if (reduceMotion) {
            ctx.clearRect(0, 0, width, height);
            pairs.forEach(function (pair, index) {
                drawPair(pair, index, 0);
            });
            return;
        }
        if (!frame) {
            frame = requestAnimationFrame(draw);
        }
    });
})();
