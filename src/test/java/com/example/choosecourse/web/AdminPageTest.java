package com.example.choosecourse.web;

import com.example.choosecourse.service.AdminCourseService;
import com.example.choosecourse.service.AdminStudentService;
import com.example.choosecourse.service.CourseImportService;
import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.StudentQueryService;
import com.example.choosecourse.web.dto.CourseImportError;
import com.example.choosecourse.web.dto.CourseImportResult;
import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.PageResult;
import com.example.choosecourse.web.dto.StudentRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_pages;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class AdminPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseQueryService courseQueryService;

    @MockitoBean
    private StudentQueryService studentQueryService;

    @MockitoBean
    private AdminCourseService adminCourseService;

    @MockitoBean
    private AdminStudentService adminStudentService;

    @MockitoBean
    private CourseImportService courseImportService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void courseListRenders() throws Exception {
        when(courseQueryService.pageAll(any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(courseRow()), 1, 10, 1));

        mockMvc.perform(get("/admin/courses"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试课程")))
                .andExpect(content().string(containsString("新增课程")))
                .andExpect(content().string(containsString("批量导入")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void courseFormRenders() throws Exception {
        when(courseQueryService.listAll()).thenReturn(List.of(courseRow()));

        mockMvc.perform(get("/admin/courses/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("课程号")))
                .andExpect(content().string(containsString("创建课程")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void studentListRenders() throws Exception {
        when(studentQueryService.search(any())).thenReturn(List.of(studentRow()));

        mockMvc.perform(get("/admin/students"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试学生")))
                .andExpect(content().string(containsString("新增学生")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void studentFormRenders() throws Exception {
        when(courseQueryService.listAll()).thenReturn(List.of(courseRow()));

        mockMvc.perform(get("/admin/students/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("学号")))
                .andExpect(content().string(containsString("创建学生")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importPageRenders() throws Exception {
        mockMvc.perform(get("/admin/courses/import"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("批量导入课程")))
                .andExpect(content().string(containsString("下载 CSV 模板")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importTemplateDownloadsCsv() throws Exception {
        mockMvc.perform(get("/admin/courses/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("course-import-template.csv")))
                .andExpect(content().string(containsString("课程号,课程名称")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importShowsValidationErrors() throws Exception {
        when(courseImportService.importCsv(any())).thenReturn(new CourseImportResult(
                List.of(new CourseImportError(2, "课程名称不能为空")), List.of(), 0, 0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.csv", "text/csv",
                "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/admin/courses/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("课程名称不能为空")))
                .andExpect(content().string(containsString("未导入任何课程")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importSuccessRedirects() throws Exception {
        when(courseImportService.importCsv(any())).thenReturn(
                new CourseImportResult(List.of(), List.of(), 2, 3));

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.csv", "text/csv",
                "x".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/admin/courses/import").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses"))
                .andExpect(flash().attribute("message", containsString("新增 2 门课程")));
    }

    @Test
    @WithMockUser(username = "20200001", roles = "STUDENT")
    void studentCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin/courses"))
                .andExpect(status().isForbidden());
    }

    private CourseRow courseRow() {
        return new CourseRow("t001", "测试课程", "张老师", "A101",
                2, 2, 0, 2, "周一 08:30-10:10", "-",
                true, false, false, false, false);
    }

    private StudentRow studentRow() {
        return new StudentRow("20200001", "测试学生", "2020级", "计算机", "信息学院", "13800000000");
    }
}
