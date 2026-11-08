package com.example.choosecourse.web.dto;

public enum EnrollmentOutcome {
    OK("选课成功"),
    ALREADY_COMPLETED("已经修过该课程"),
    PREREQUISITE_MISSING("先修课未完成"),
    ALREADY_SELECTED("已经选过该课程"),
    TIME_CONFLICT("与已选课程上课时间冲突"),
    CREDIT_LIMIT_EXCEEDED("超过本学期学分上限"),
    FULL("人数已满"),
    COURSE_NOT_FOUND("课程不存在"),
    COURSE_INACTIVE("课程已停开"),
    STUDENT_NOT_FOUND("学生不存在");

    private final String message;

    EnrollmentOutcome(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
