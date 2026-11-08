/*
 * 学分目标礼炮：
 * - 选课清单累计学分跨过目标时触发
 * - 页面加载时如果真实已选学分已经达标，每个会话只庆祝一次
 * - prefers-reduced-motion 下只显示提示，不放礼炮
 */
(function () {
    'use strict';

    var root = document.getElementById('credit-celebration');
    if (!root) {
        return;
    }

    var goal = parseInt(root.getAttribute('data-goal') || '20', 10);
    var maxCredits = parseInt(root.getAttribute('data-max-credits') || '30', 10);
    var initialTotal = parseInt(root.getAttribute('data-selected-credits') || '0', 10);
    if (!isFinite(goal) || goal <= 0) {
        goal = 20;
    }
    if (!isFinite(maxCredits) || maxCredits <= 0) {
        maxCredits = 30;
    }
    if (!isFinite(initialTotal) || initialTotal < 0) {
        initialTotal = 0;
    }

    var summary = document.getElementById('cart-summary');
    var goalStatus = document.getElementById('credit-goal-status');
    var storageKey = 'creditGoalCelebrated:' + goal;
    var lastTotal = initialTotal;
    var currentTotal = initialTotal;
    var celebrated = hasCelebrated();

    function hasCelebrated() {
        try {
            return sessionStorage.getItem(storageKey) === '1';
        } catch (e) {
            return false;
        }
    }

    function markCelebrated() {
        celebrated = true;
        try {
            sessionStorage.setItem(storageKey, '1');
        } catch (e) {
            // 隐私模式忽略
        }
    }

    function updateStatus(total) {
        var reached = total >= goal;
        if (summary) {
            summary.classList.toggle('goal-reached', reached);
        }
        if (goalStatus) {
            goalStatus.classList.toggle('reached', reached);
            goalStatus.textContent = reached
                    ? '🎉 学分目标已达成'
                    : '目标 ' + goal + ' 学分';
        }
        return reached;
    }

    function celebrate(total) {
        if (celebrated) {
            return;
        }
        markCelebrated();
        showBanner(total);
        if (window.matchMedia
                && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }
        launchConfetti();
    }

    function showBanner(total) {
        var banner = document.createElement('div');
        banner.className = 'credit-cannon-banner';
        banner.setAttribute('role', 'status');
        banner.innerHTML = '<strong>🎉 学分目标达成</strong><span>累计 '
                + total + ' / ' + maxCredits + ' 学分</span>';
        document.body.appendChild(banner);
        requestAnimationFrame(function () {
            banner.classList.add('show');
        });
        setTimeout(function () {
            banner.classList.remove('show');
        }, 3200);
        setTimeout(function () {
            if (banner.parentNode) {
                banner.parentNode.removeChild(banner);
            }
        }, 3800);
    }

    function launchConfetti() {
        var canvas = document.createElement('canvas');
        canvas.className = 'credit-cannon-canvas';
        canvas.setAttribute('aria-hidden', 'true');
        document.body.appendChild(canvas);

        var ctx = canvas.getContext('2d');
        if (!ctx) {
            canvas.remove();
            return;
        }

        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        var width = window.innerWidth;
        var height = window.innerHeight;
        canvas.width = Math.round(width * dpr);
        canvas.height = Math.round(height * dpr);
        canvas.style.width = width + 'px';
        canvas.style.height = height + 'px';
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

        var colors = ['#38bdf8', '#818cf8', '#f472b6', '#fbbf24',
            '#34d399', '#fb7185', '#a78bfa', '#fde68a'];
        var particles = [];
        var smallScreen = width < 680;

        function queueBurst(x, y, angle, delay, count) {
            var sizeFactor = smallScreen ? 0.75 : 1;
            count = Math.round(count * sizeFactor);
            for (var i = 0; i < count; i++) {
                var spread = (Math.random() - 0.5) * 0.55;
                var speed = 12 + Math.random() * 9;
                var direction = angle + spread;
                var life = 110 + Math.random() * 70;
                particles.push({
                    x: x + (Math.random() - 0.5) * 14,
                    y: y + (Math.random() - 0.5) * 8,
                    vx: Math.cos(direction) * speed,
                    vy: Math.sin(direction) * speed,
                    gravity: 0.24 + Math.random() * 0.08,
                    drag: 0.988 + Math.random() * 0.006,
                    rotation: Math.random() * Math.PI * 2,
                    rotationSpeed: (Math.random() - 0.5) * 0.45,
                    width: 5 + Math.random() * 6,
                    height: 3 + Math.random() * 6,
                    color: colors[(Math.random() * colors.length) | 0],
                    shape: Math.random() < 0.24 ? 'circle'
                            : (Math.random() < 0.58 ? 'rect' : 'ribbon'),
                    delay: delay,
                    life: life,
                    maxLife: life
                });
            }
        }

        // 左右礼炮 + 中间金色礼花，错峰发射
        queueBurst(0, height + 8, -Math.PI / 3, 0, 60);
        queueBurst(width, height + 8, -2 * Math.PI / 3, 0, 60);
        queueBurst(width / 2, height + 8, -Math.PI / 2, 170, 70);
        queueBurst(0, height + 8, -Math.PI / 3, 380, 42);
        queueBurst(width, height + 8, -2 * Math.PI / 3, 380, 42);

        var last = performance.now();
        var frameHandle = null;

        function frame(now) {
            var elapsed = Math.min(32, now - last);
            var dt = elapsed / 16.6667;
            last = now;
            ctx.clearRect(0, 0, width, height);

            for (var i = particles.length - 1; i >= 0; i--) {
                var particle = particles[i];
                if (particle.delay > 0) {
                    particle.delay -= elapsed;
                    continue;
                }

                particle.vx *= Math.pow(particle.drag, dt);
                particle.vy = particle.vy * Math.pow(particle.drag, dt)
                        + particle.gravity * dt;
                particle.x += particle.vx * dt;
                particle.y += particle.vy * dt;
                particle.rotation += particle.rotationSpeed * dt;
                particle.life -= dt;

                if (particle.life <= 0
                        || particle.y > height + 70
                        || particle.x < -80
                        || particle.x > width + 80) {
                    particles.splice(i, 1);
                    continue;
                }

                var alpha = Math.min(1, particle.life / 28);
                ctx.save();
                ctx.globalAlpha = alpha;
                ctx.translate(particle.x, particle.y);
                ctx.rotate(particle.rotation);
                ctx.fillStyle = particle.color;
                if (particle.shape === 'circle') {
                    ctx.beginPath();
                    ctx.arc(0, 0, particle.width / 2, 0, Math.PI * 2);
                    ctx.fill();
                } else if (particle.shape === 'ribbon') {
                    ctx.fillRect(-particle.width / 2, -particle.height / 4,
                            particle.width, particle.height / 2);
                    ctx.fillRect(-particle.width / 4, -particle.height / 2,
                            particle.width / 2, particle.height);
                } else {
                    ctx.fillRect(-particle.width / 2, -particle.height / 2,
                            particle.width, particle.height);
                }
                ctx.restore();
            }

            if (particles.length > 0) {
                frameHandle = requestAnimationFrame(frame);
            } else {
                if (frameHandle) {
                    cancelAnimationFrame(frameHandle);
                }
                canvas.remove();
            }
        }

        frameHandle = requestAnimationFrame(frame);
    }

    updateStatus(initialTotal);

    document.addEventListener('credit-total-change', function (event) {
        var total = Number(event.detail && event.detail.total);
        if (!isFinite(total) || total < 0) {
            return;
        }
        currentTotal = total;
        var reached = updateStatus(total);
        if (reached && lastTotal < goal) {
            celebrate(total);
        }
        lastTotal = total;
    });

    if (initialTotal >= goal && !celebrated) {
        setTimeout(function () {
            updateStatus(currentTotal);
            if (currentTotal >= goal) {
                celebrate(currentTotal);
            }
        }, 700);
    }
})();
