package com.example.choosecourse.web.dto;

import java.util.List;

public class ScheduleRow {

    private final String label;
    private final List<String> cells;

    public ScheduleRow(String label, List<String> cells) {
        this.label = label;
        this.cells = cells;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getCells() {
        return cells;
    }
}
