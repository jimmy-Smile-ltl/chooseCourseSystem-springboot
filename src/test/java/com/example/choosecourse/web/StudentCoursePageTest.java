package com.example.choosecourse.web;

import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.EnrollmentService;
import com.example.choosecourse.service.StudentQueryService;
import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.EnrollmentOutcome;
import com.example.choosecourse.web.dto.PageResult;
import com.example.choosecourse.web.dto.ScheduleEntry;
import com.example.choosecourse.web.dto.StudentRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:choose_course_page;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class StudentCoursePageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseQueryService courseQueryService;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @MockitoBean
    private StudentQueryService studentQueryService;

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void coursePageRendersWithStudentData() throws Exception {
        when(courseQueryService.sessionJson()).thenReturn("{}");
        var selected = new CourseRow("t001", "测试课程", "张老师", "A101",
                2, 2, 1, 1, "周一 08:30-10:10", "-",
                true, true, false, false, false);
        when(courseQueryService.pageForStudent(anyString(), any(), anyBoolean(), anyBoolean(),
                anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(selected), 1, 10, 1));
        when(courseQueryService.listSelected(anyString())).thenReturn(List.of(selected));

        mockMvc.perform(get("/student/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试课程")))
                .andExpect(content().string(containsString("已选")))
                .andExpect(content().string(containsString("目标")))
                .andExpect(content().string(containsString("data-goal=\"20\"")))
                .andExpect(content().string(containsString("credit-hud")));
    }

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void enrollActionCallsServiceAndRedirects() throws Exception {
        when(enrollmentService.enroll("20200001", "t001")).thenReturn(EnrollmentOutcome.OK);

        mockMvc.perform(post("/student/enroll")
                        .with(csrf())
                        .param("cno", "t001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attributeExists("message"));

        verify(enrollmentService).enroll("20200001", "t001");
    }

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void schedulePageRendersGrid() throws Exception {
        when(courseQueryService.listSchedule("20200001")).thenReturn(List.of(
                new ScheduleEntry("t001", "测试课程", "张老师", "A101", 1,
                        java.time.LocalTime.of(8, 30), java.time.LocalTime.of(10, 10))
        ));
        when(courseQueryService.listSelected(anyString())).thenReturn(List.of(
                new CourseRow("t001", "测试课程", "张老师", "A101",
                        2, 2, 1, 1, "周一 08:30-10:10", "-",
                        true, true, false, false, false)
        ));

        mockMvc.perform(get("/student/schedule"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试课程")))
                .andExpect(content().string(containsString("第一节")))
                .andExpect(content().string(containsString("schedule-fx")));
    }

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void passwordPageRenders() throws Exception {
        mockMvc.perform(get("/student/password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("修改密码")));
    }

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void phonePageRenders() throws Exception {
        when(studentQueryService.findBySno("20200001")).thenReturn(java.util.Optional.of(
                new StudentRow("20200001", "测试学生", "2020级", "计算机", "信息学院", "13800000000")
        ));

        mockMvc.perform(get("/student/phone"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("13800000000")));
    }
}
