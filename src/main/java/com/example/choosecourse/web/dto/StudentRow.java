package com.example.choosecourse.web.dto;

public class StudentRow {

    private final String sno;
    private final String name;
    private final String grade;
    private final String major;
    private final String college;
    private final String phone;

    public StudentRow(String sno, String name, String grade, String major, String college, String phone) {
        this.sno = sno;
        this.name = name;
        this.grade = grade;
        this.major = major;
        this.college = college;
        this.phone = phone;
    }

    public String getSno() {
        return sno;
    }

    public String getName() {
        return name;
    }

    public String getGrade() {
        return grade;
    }

    public String getMajor() {
        return major;
    }

    public String getCollege() {
        return college;
    }

    public String getPhone() {
        return phone;
    }
}
