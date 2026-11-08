package com.example.choosecourse.web.dto;

import java.util.List;

public class CourseImportResult {

    private final List<CourseImportError> errors;
    private final List<String> warnings;
    private final int coursesImported;
    private final int sessionsImported;

    public CourseImportResult(List<CourseImportError> errors, List<String> warnings,
                              int coursesImported, int sessionsImported) {
        this.errors = List.copyOf(errors);
        this.warnings = List.copyOf(warnings);
        this.coursesImported = coursesImported;
        this.sessionsImported = sessionsImported;
    }

    public List<CourseImportError> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getCoursesImported() {
        return coursesImported;
    }

    public int getSessionsImported() {
        return sessionsImported;
    }

    public boolean isSuccess() {
        return errors.isEmpty() && coursesImported > 0;
    }
}
