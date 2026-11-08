package com.example.choosecourse.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class StudentForm {

    @NotBlank(message = "学号不能为空")
    @Size(max = 20, message = "学号最长 20 个字符")
    private String sno;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名最长 50 个字符")
    private String name;

    @Size(max = 20, message = "年级最长 20 个字符")
    private String grade;

    @Size(max = 50, message = "专业最长 50 个字符")
    private String major;

    @Size(max = 50, message = "学院最长 50 个字符")
    private String college;

    @Size(max = 20, message = "手机最长 20 个字符")
    private String phone;

    /**
     * 新增时留空默认 123456；编辑时留空表示不修改密码。
     */
    @Size(max = 100, message = "密码最长 100 个字符")
    private String password;

    private List<String> completedCnos = new ArrayList<>();

    public String getSno() {
        return sno;
    }

    public void setSno(String sno) {
        this.sno = sno;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getCompletedCnos() {
        return completedCnos;
    }

    public void setCompletedCnos(List<String> completedCnos) {
        this.completedCnos = completedCnos == null ? new ArrayList<>() : completedCnos;
    }
}
