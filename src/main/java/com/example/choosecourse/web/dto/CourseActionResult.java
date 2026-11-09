package com.example.choosecourse.web.dto;

/**
 * 批量选课/退课的单条结果，用于页面按列对齐展示。
 */
public record CourseActionResult(String cno, String message, boolean success) {
}
