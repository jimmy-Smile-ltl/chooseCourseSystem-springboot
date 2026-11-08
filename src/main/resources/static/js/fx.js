/*
 * 登录/忘记密码页的粒子网络 + 鼠标跟随 + 点击特效。
 * 不依赖任何第三方库；Canvas 固定在全屏、pointer-events:none，不影响表单操作。
 */
(function () {
    'use strict';

    if (window.__FX_INITIALIZED__) {
        return;
    }
    window.__FX_INITIALIZED__ = true;

    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        return;
    }

    var canvas = document.createElement('canvas');
    canvas.className = 'fx-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.appendChild(canvas);

    var ctx = canvas.getContext('2d');
    if (!ctx) {
        return;
    }

    var width = 0;
    var height = 0;
    var dpr = 1;
    var particles = [];
    var bursts = [];
    var mouse = { x: null, y: null, active: false };

    var PALETTE = [
        'rgba(100, 220, 255, ',
        'rgba(150, 170, 255, ',
        'rgba(210, 150, 255, ',
        'rgba(255, 160, 220, ',
        'rgba(220, 250, 255, '
    ];

    var PHRASES = [
        '封印解除！',
        '星辉爆裂！',
        '魔法回路，启动！',
        '以我之名，选课！',
        '结界展开！',
        '中二之力，觉醒！',
        '爆裂吧，现实！',
        '命运开始转动。'
    ];

    var MAX_PARTICLES = 105;
    var LINK_DISTANCE = 135;
    var MOUSE_DISTANCE = 195;
    var ATTRACT_RADIUS = 240;

    function createParticle() {
        return {
            x: Math.random() * width,
            y: Math.random() * height,
            vx: (Math.random() - 0.5) * 0.45,
            vy: (Math.random() - 0.5) * 0.45,
            r: Math.random() * 1.7 + 0.6,
            color: PALETTE[Math.floor(Math.random() * PALETTE.length)]
        };
    }

    function resize() {
        dpr = Math.min(window.devicePixelRatio || 1, 2);
        width = window.innerWidth;
        height = window.innerHeight;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        var count = Math.max(45, Math.min(MAX_PARTICLES, Math.floor(width * height / 16000)));
        particles = [];
        for (var i = 0; i < count; i++) {
            particles.push(createParticle());
        }
    }

    function spawnBurst(x, y) {
        for (var i = 0; i < 22; i++) {
            var angle = Math.random() * Math.PI * 2;
            var speed = Math.random() * 3.4 + 1.2;
            bursts.push({
                type: 'spark',
                x: x,
                y: y,
                vx: Math.cos(angle) * speed,
                vy: Math.sin(angle) * speed,
                life: 1,
                decay: 0.012 + Math.random() * 0.018,
                size: Math.random() * 2.2 + 1,
                color: PALETTE[Math.floor(Math.random() * PALETTE.length)]
            });
        }
        bursts.push({ type: 'ring', x: x, y: y, life: 1, decay: 0.035 });
    }

    function spawnText(x, y) {
        var el = document.createElement('span');
        el.className = 'fx-text';
        el.textContent = PHRASES[Math.floor(Math.random() * PHRASES.length)];
        el.style.left = x + 'px';
        el.style.top = y + 'px';
        document.body.appendChild(el);
        setTimeout(function () {
            el.remove();
        }, 1700);
    }

    function draw() {
        ctx.clearRect(0, 0, width, height);
        ctx.globalCompositeOperation = 'lighter';

        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];

            if (mouse.active && mouse.x !== null) {
                var mdx = mouse.x - p.x;
                var mdy = mouse.y - p.y;
                var mdist2 = mdx * mdx + mdy * mdy;
                if (mdist2 < ATTRACT_RADIUS * ATTRACT_RADIUS && mdist2 > 4) {
                    var force = 0.00045 * (1 - Math.sqrt(mdist2) / ATTRACT_RADIUS);
                    p.vx += mdx * force;
                    p.vy += mdy * force;
                }
            }

            p.vx *= 0.994;
            p.vy *= 0.994;
            if (Math.abs(p.vx) < 0.04) {
                p.vx += (Math.random() - 0.5) * 0.018;
            }
            if (Math.abs(p.vy) < 0.04) {
                p.vy += (Math.random() - 0.5) * 0.018;
            }

            var speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (speed > 1.1) {
                p.vx = p.vx / speed * 1.1;
                p.vy = p.vy / speed * 1.1;
            }

            p.x += p.vx;
            p.y += p.vy;
            if (p.x < 0) {
                p.x = 0;
                p.vx *= -1;
            } else if (p.x > width) {
                p.x = width;
                p.vx *= -1;
            }
            if (p.y < 0) {
                p.y = 0;
                p.vy *= -1;
            } else if (p.y > height) {
                p.y = height;
                p.vy *= -1;
            }
        }

        for (var a = 0; a < particles.length; a++) {
            var pa = particles[a];

            for (var b = a + 1; b < particles.length; b++) {
                var pb = particles[b];
                var dx = pa.x - pb.x;
                var dy = pa.y - pb.y;
                var distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < LINK_DISTANCE) {
                    var alpha = (1 - distance / LINK_DISTANCE) * 0.34;
                    ctx.strokeStyle = 'rgba(160, 225, 255, ' + alpha + ')';
                    ctx.lineWidth = 0.7;
                    ctx.beginPath();
                    ctx.moveTo(pa.x, pa.y);
                    ctx.lineTo(pb.x, pb.y);
                    ctx.stroke();
                }
            }

            if (mouse.active && mouse.x !== null) {
                var md = Math.sqrt(
                    (pa.x - mouse.x) * (pa.x - mouse.x) +
                    (pa.y - mouse.y) * (pa.y - mouse.y)
                );
                if (md < MOUSE_DISTANCE) {
                    var ma = (1 - md / MOUSE_DISTANCE) * 0.75;
                    ctx.strokeStyle = 'rgba(120, 230, 255, ' + ma + ')';
                    ctx.lineWidth = 1.1;
                    ctx.beginPath();
                    ctx.moveTo(pa.x, pa.y);
                    ctx.lineTo(mouse.x, mouse.y);
                    ctx.stroke();
                }
            }

            ctx.beginPath();
            ctx.fillStyle = pa.color + '0.9)';
            ctx.shadowColor = pa.color + '1)';
            ctx.shadowBlur = 12;
            ctx.arc(pa.x, pa.y, pa.r, 0, Math.PI * 2);
            ctx.fill();
            ctx.shadowBlur = 0;
        }

        for (var k = bursts.length - 1; k >= 0; k--) {
            var burst = bursts[k];
            burst.life -= burst.decay;
            if (burst.life <= 0) {
                bursts.splice(k, 1);
                continue;
            }

            if (burst.type === 'spark') {
                burst.x += burst.vx;
                burst.y += burst.vy;
                burst.vx *= 0.98;
                burst.vy *= 0.98;
                ctx.beginPath();
                ctx.fillStyle = burst.color + (burst.life * 0.95) + ')';
                ctx.shadowColor = burst.color + burst.life + ')';
                ctx.shadowBlur = 14;
                ctx.arc(burst.x, burst.y, burst.size * burst.life, 0, Math.PI * 2);
                ctx.fill();
                ctx.shadowBlur = 0;
            } else {
                var radius = (1 - burst.life) * 70 + 6;
                ctx.beginPath();
                ctx.strokeStyle = 'rgba(140, 230, 255, ' + (burst.life * 0.8) + ')';
                ctx.lineWidth = 2;
                ctx.arc(burst.x, burst.y, radius, 0, Math.PI * 2);
                ctx.stroke();
                ctx.beginPath();
                ctx.strokeStyle = 'rgba(210, 150, 255, ' + (burst.life * 0.5) + ')';
                ctx.lineWidth = 1;
                ctx.arc(burst.x, burst.y, radius * 0.7, 0, Math.PI * 2);
                ctx.stroke();
            }
        }

        if (mouse.active && mouse.x !== null) {
            var glow = ctx.createRadialGradient(mouse.x, mouse.y, 0, mouse.x, mouse.y, 95);
            glow.addColorStop(0, 'rgba(120, 230, 255, 0.18)');
            glow.addColorStop(0.5, 'rgba(150, 130, 255, 0.08)');
            glow.addColorStop(1, 'rgba(120, 230, 255, 0)');
            ctx.fillStyle = glow;
            ctx.beginPath();
            ctx.arc(mouse.x, mouse.y, 95, 0, Math.PI * 2);
            ctx.fill();
        }

        ctx.globalCompositeOperation = 'source-over';
        window.requestAnimationFrame(draw);
    }

    window.addEventListener('resize', resize);
    window.addEventListener('mousemove', function (event) {
        mouse.x = event.clientX;
        mouse.y = event.clientY;
        mouse.active = true;
    }, { passive: true });
    window.addEventListener('mouseleave', function () {
        mouse.active = false;
        mouse.x = null;
        mouse.y = null;
    });

    document.addEventListener('pointerdown', function (event) {
        if (event.button !== 0) {
            return;
        }
        spawnBurst(event.clientX, event.clientY);
        if (event.target && event.target.closest &&
            event.target.closest('input, textarea, select')) {
            return;
        }
        spawnText(event.clientX, event.clientY);
    }, { passive: true });

    resize();
    draw();
})();
