/*
 * 选课页增强：
 * 1. 勾选时实时统计学分、检测勾选课程之间的时间冲突
 * 2. 一键智能推荐当前页一组不冲突、不超学分的课程
 * 搜索/筛选/分页由服务端完成；服务端仍会在提交时做最终校验。
 */
(function () {
    'use strict';

    var form = document.getElementById('courseForm');
    if (!form) {
        return;
    }

    var maxCredits = parseInt(form.getAttribute('data-max-credits') || '30', 10);
    var selectedCredits = parseInt(form.getAttribute('data-selected-credits') || '0', 10);
    var rows = Array.prototype.slice.call(document.querySelectorAll('tr.course-row'));
    var smartPickButton = document.getElementById('smart-pick');
    var clearPicksButton = document.getElementById('clear-picks');
    var summary = document.getElementById('cart-summary');
    var countEl = document.getElementById('pick-count');
    var creditsEl = document.getElementById('pick-credits');
    var totalEl = document.getElementById('pick-total');
    var conflictsEl = document.getElementById('pick-conflicts');

    var sessionData = {};
    try {
        var jsonNode = document.getElementById('session-data');
        if (jsonNode && jsonNode.textContent.trim()) {
            sessionData = JSON.parse(jsonNode.textContent);
        }
    } catch (e) {
        sessionData = {};
    }

    function sessionsOf(cno) {
        return sessionData[cno] || [];
    }

    function overlaps(a, b) {
        return a.d === b.d && a.s < b.e && b.s < a.e;
    }

    function haveConflict(cnoA, cnoB) {
        var a = sessionsOf(cnoA);
        var b = sessionsOf(cnoB);
        for (var i = 0; i < a.length; i++) {
            for (var j = 0; j < b.length; j++) {
                if (overlaps(a[i], b[j])) {
                    return true;
                }
            }
        }
        return false;
    }

    function checkboxOf(row) {
        return row.querySelector('.course-checkbox');
    }

    function checkedRows() {
        return rows.filter(function (row) {
            var checkbox = checkboxOf(row);
            return checkbox && checkbox.checked;
        });
    }

    function conflictPairs(picked) {
        var pairs = [];
        for (var i = 0; i < picked.length; i++) {
            for (var j = i + 1; j < picked.length; j++) {
                if (haveConflict(picked[i].getAttribute('data-cno'),
                        picked[j].getAttribute('data-cno'))) {
                    pairs.push({
                        a: picked[i],
                        b: picked[j],
                        label: picked[i].getAttribute('data-name')
                                + ' ↔ ' + picked[j].getAttribute('data-name')
                    });
                }
            }
        }
        return pairs;
    }

    function updateSummary() {
        var picked = checkedRows();
        var credits = picked.reduce(function (sum, row) {
            return sum + (parseInt(row.getAttribute('data-credits'), 10) || 0);
        }, 0);
        var pairs = conflictPairs(picked);
        var conflicts = pairs.map(function (pair) {
            return pair.label;
        });

        var total = selectedCredits + credits;
        countEl.textContent = String(picked.length);
        creditsEl.textContent = String(credits);
        totalEl.textContent = String(total);
        conflictsEl.textContent = conflicts.length
                ? ' · 冲突：' + conflicts.join('；')
                : '';

        summary.classList.toggle('over-limit', total > maxCredits);
        summary.classList.toggle('has-conflict', conflicts.length > 0);

        document.dispatchEvent(new CustomEvent('credit-total-change', {
            detail: {total: total, maxCredits: maxCredits}
        }));
        document.dispatchEvent(new CustomEvent('course-conflicts-change', {
            detail: {
                pairs: pairs.map(function (pair) {
                    return {
                        a: pair.a.getAttribute('data-cno'),
                        b: pair.b.getAttribute('data-cno')
                    };
                })
            }
        }));
    }

    function clearPicks() {
        rows.forEach(function (row) {
            var checkbox = checkboxOf(row);
            if (checkbox && !checkbox.disabled) {
                checkbox.checked = false;
            }
        });
        updateSummary();
    }

    function smartPickCourses() {
        clearPicks();

        var candidates = rows.filter(function (row) {
            var checkbox = checkboxOf(row);
            return checkbox && !checkbox.disabled;
        });

        candidates.sort(function (a, b) {
            var creditsDiff = (parseInt(b.getAttribute('data-credits'), 10) || 0)
                    - (parseInt(a.getAttribute('data-credits'), 10) || 0);
            if (creditsDiff !== 0) {
                return creditsDiff;
            }
            return (parseInt(b.getAttribute('data-remaining'), 10) || 0)
                    - (parseInt(a.getAttribute('data-remaining'), 10) || 0);
        });

        var picked = [];
        var totalCredits = 0;
        var remainingCredits = maxCredits - selectedCredits;
        candidates.forEach(function (row) {
            var credits = parseInt(row.getAttribute('data-credits'), 10) || 0;
            if (totalCredits + credits > remainingCredits) {
                return;
            }
            var cno = row.getAttribute('data-cno');
            var conflicts = picked.some(function (other) {
                return haveConflict(cno, other.getAttribute('data-cno'));
            });
            if (!conflicts) {
                checkboxOf(row).checked = true;
                picked.push(row);
                totalCredits += credits;
            }
        });
        updateSummary();
    }

    rows.forEach(function (row) {
        var checkbox = checkboxOf(row);
        if (checkbox) {
            checkbox.addEventListener('change', updateSummary);
        }
    });

    if (smartPickButton) {
        smartPickButton.addEventListener('click', smartPickCourses);
    }
    if (clearPicksButton) {
        clearPicksButton.addEventListener('click', clearPicks);
    }

    form.addEventListener('submit', function (event) {
        var picked = checkedRows();
        if (!picked.length) {
            event.preventDefault();
            alert('请先勾选要选的课程。');
            return;
        }
        var credits = picked.reduce(function (sum, row) {
            return sum + (parseInt(row.getAttribute('data-credits'), 10) || 0);
        }, 0);
        var total = selectedCredits + credits;
        if (total > maxCredits) {
            event.preventDefault();
            alert('已选 ' + selectedCredits + ' 学分，本次勾选 ' + credits
                    + ' 学分，累计 ' + total + ' 超过上限 ' + maxCredits + '。');
            return;
        }
        var conflicts = conflictsAmong(picked);
        if (conflicts.length) {
            event.preventDefault();
            alert('勾选课程存在时间冲突：' + conflicts.join('；'));
        }
    });

    updateSummary();
})();
