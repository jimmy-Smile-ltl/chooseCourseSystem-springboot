package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.ScheduleBlock;
import com.example.choosecourse.web.dto.ScheduleEntry;
import com.example.choosecourse.web.dto.ScheduleRow;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduleService {

    private static final int PERIOD_COUNT = 12;
    private static final int DAYS = 7;

    private static final LocalTime[] PERIOD_STARTS = {
            LocalTime.of(8, 30),
            LocalTime.of(9, 25),
            LocalTime.of(10, 20),
            LocalTime.of(11, 15),
            LocalTime.of(13, 0),
            LocalTime.of(13, 55),
            LocalTime.of(14, 50),
            LocalTime.of(15, 45),
            LocalTime.of(16, 40),
            LocalTime.of(18, 30),
            LocalTime.of(19, 25),
            LocalTime.of(20, 20)
    };

    private static final String[] PERIOD_LABELS = {
            "第一节", "第二节", "第三节", "第四节", "第五节", "第六节",
            "第七节", "第八节", "第九节", "第十节", "第十一节", "第十二节"
    };

    public List<ScheduleRow> build(List<ScheduleEntry> entries) {
        String[][] grid = new String[PERIOD_COUNT][DAYS];

        for (ScheduleEntry entry : entries) {
            int day = entry.getDayOfWeek();
            if (day < 1 || day > DAYS) {
                continue;
            }
            int start = periodOf(entry.getStartTime());
            int end = periodOf(entry.getEndTime());
            if (end < start) {
                end = start;
            }
            String text = entry.getName()
                    + "\n" + entry.getTeacher() + " · " + entry.getLocation();
            for (int period = start; period <= end; period++) {
                int row = period - 1;
                int col = day - 1;
                grid[row][col] = grid[row][col] == null ? text : grid[row][col] + "\n\n" + text;
            }
        }

        List<ScheduleRow> rows = new ArrayList<>(PERIOD_COUNT);
        for (int period = 0; period < PERIOD_COUNT; period++) {
            List<String> cells = new ArrayList<>(DAYS);
            for (int day = 0; day < DAYS; day++) {
                cells.add(grid[period][day] == null ? "" : grid[period][day]);
            }
            rows.add(new ScheduleRow(PERIOD_LABELS[period], cells));
        }
        return rows;
    }

    /**
     * 课表格块模式：每门课一个块，支持跨多节；块之间重叠时标记冲突。
     */
    public List<ScheduleBlock> buildBlocks(List<ScheduleEntry> entries) {
        List<ScheduleBlock> blocks = new ArrayList<>(entries.size());
        for (ScheduleEntry entry : entries) {
            int start = periodOf(entry.getStartTime());
            int end = periodOf(entry.getEndTime());
            if (end < start) {
                end = start;
            }
            blocks.add(new ScheduleBlock(
                    entry.getCno(),
                    entry.getName(),
                    entry.getTeacher(),
                    entry.getLocation(),
                    entry.getDayOfWeek(),
                    start,
                    end,
                    entry.getCno().hashCode()
            ));
        }

        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                ScheduleBlock a = blocks.get(i);
                ScheduleBlock b = blocks.get(j);
                if (a.getDayOfWeek() == b.getDayOfWeek()
                        && a.getStartPeriod() <= b.getEndPeriod()
                        && b.getStartPeriod() <= a.getEndPeriod()) {
                    a.setConflict(true);
                    b.setConflict(true);
                }
            }
        }
        return blocks;
    }

    /**
     * 把实际时间映射到第几节：取最后一个开始时间早于等于该时刻的节次。
     * 例如 10:10 -> 第二节，12:00 -> 第四节，21:05 -> 第十二节。
     */
    public int periodOf(LocalTime time) {
        if (time == null) {
            return 1;
        }
        int period = 1;
        for (int i = 0; i < PERIOD_STARTS.length; i++) {
            if (!time.isBefore(PERIOD_STARTS[i])) {
                period = i + 1;
            }
        }
        return period;
    }
}
