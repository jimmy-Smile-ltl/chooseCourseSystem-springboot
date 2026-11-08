package com.example.choosecourse.web.dto;

public class CourseImportError {

    private final int line;
    private final String message;

    public CourseImportError(int line, String message) {
        this.line = line;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }
}
