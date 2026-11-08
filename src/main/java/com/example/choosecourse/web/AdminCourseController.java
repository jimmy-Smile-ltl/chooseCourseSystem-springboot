package com.example.choosecourse.web;

import com.example.choosecourse.service.AdminCourseService;
import com.example.choosecourse.service.CourseImportService;
import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.web.dto.CourseImportResult;
import com.example.choosecourse.web.form.CourseForm;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/admin/courses")
public class AdminCourseController {

    private static final Logger log = LoggerFactory.getLogger(AdminCourseController.class);

    private final CourseQueryService courseQueryService;
    private final AdminCourseService adminCourseService;
    private final CourseImportService courseImportService;
    private final int pageSize;

    public AdminCourseController(CourseQueryService courseQueryService,
                                 AdminCourseService adminCourseService,
                                 CourseImportService courseImportService,
                                 @Value("${app.page-size:20}") int pageSize) {
        this.courseQueryService = courseQueryService;
        this.adminCourseService = adminCourseService;
        this.courseImportService = courseImportService;
        this.pageSize = pageSize;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String q,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        var coursePage = courseQueryService.pageAll(q, page, pageSize);
        model.addAttribute("courses", coursePage.getItems());
        model.addAttribute("coursePage", coursePage);
        model.addAttribute("q", q == null ? "" : q);
        return "admin/courses";
    }

    @GetMapping("/import")
    public String importForm() {
        return "admin/course-import";
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        String csv = "\uFEFF" + """
                课程号,课程名称,教师,地点,容量,学分,星期,开始时间,结束时间,先修课程号,是否开放
                demo101,示例课程,张老师,A101,60,3,周一,08:30,10:10,,是
                demo101,示例课程,张老师,A101,60,3,周三,08:30,10:10,,是
                demo102,进阶示例,李老师,B202,40,3,周二,13:00,15:35,demo101,是
                """;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"course-import-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/import")
    public String importCourses(@RequestParam("file") MultipartFile file,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            model.addAttribute("uploadError", "请选择要导入的 CSV 文件");
            return "admin/course-import";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            model.addAttribute("uploadError", "只支持 .csv 文件");
            return "admin/course-import";
        }
        if (file.getSize() > 1_000_000L) {
            model.addAttribute("uploadError", "文件不能超过 1MB");
            return "admin/course-import";
        }

        try {
            CourseImportResult result = courseImportService.importCsv(file.getBytes());
            if (!result.getErrors().isEmpty()) {
                model.addAttribute("importResult", result);
                return "admin/course-import";
            }
            redirectAttributes.addFlashAttribute("message",
                    "导入成功：新增 " + result.getCoursesImported() + " 门课程、"
                            + result.getSessionsImported() + " 个上课时段");
            return "redirect:/admin/courses";
        } catch (IOException e) {
            log.warn("读取课程导入文件失败", e);
            model.addAttribute("uploadError", "读取上传文件失败，请重试");
            return "admin/course-import";
        } catch (DataAccessException e) {
            log.warn("批量导入课程失败，事务已回滚", e);
            model.addAttribute("uploadError",
                    "导入失败，整批数据已回滚。请检查课程号/先修课冲突后重试。");
            return "admin/course-import";
        }
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("courseForm", new CourseForm());
        model.addAttribute("editing", false);
        model.addAttribute("formAction", "/admin/courses");
        addCourseOptions(model, null);
        return "admin/course-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("courseForm") CourseForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            model.addAttribute("formAction", "/admin/courses");
            addCourseOptions(model, null);
            return "admin/course-form";
        }

        List<String> errors = adminCourseService.create(form);
        if (!errors.isEmpty()) {
            model.addAttribute("serviceErrors", errors);
            model.addAttribute("editing", false);
            model.addAttribute("formAction", "/admin/courses");
            addCourseOptions(model, null);
            return "admin/course-form";
        }

        redirectAttributes.addFlashAttribute("message", "课程 " + form.getCno().trim() + " 添加成功");
        return "redirect:/admin/courses";
    }

    @GetMapping("/{cno}/edit")
    public String editForm(@PathVariable String cno, Model model,
                           RedirectAttributes redirectAttributes) {
        return courseQueryService.findForm(cno)
                .map(form -> {
                    model.addAttribute("courseForm", form);
                    model.addAttribute("editing", true);
                    model.addAttribute("formAction", "/admin/courses/" + cno);
                    addCourseOptions(model, cno);
                    return "admin/course-form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "课程不存在: " + cno);
                    return "redirect:/admin/courses";
                });
    }

    @PostMapping("/{cno}")
    public String update(@PathVariable String cno,
                         @Valid @ModelAttribute("courseForm") CourseForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        form.setCno(cno);
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("formAction", "/admin/courses/" + cno);
            addCourseOptions(model, cno);
            return "admin/course-form";
        }

        List<String> errors = adminCourseService.update(cno, form);
        if (!errors.isEmpty()) {
            model.addAttribute("serviceErrors", errors);
            model.addAttribute("editing", true);
            model.addAttribute("formAction", "/admin/courses/" + cno);
            addCourseOptions(model, cno);
            return "admin/course-form";
        }

        redirectAttributes.addFlashAttribute("message", "课程 " + cno + " 修改成功");
        return "redirect:/admin/courses";
    }

    @PostMapping("/{cno}/toggle")
    public String toggle(@PathVariable String cno, RedirectAttributes redirectAttributes) {
        boolean ok = adminCourseService.toggleActive(cno);
        redirectAttributes.addFlashAttribute(ok ? "message" : "errorMessage",
                ok ? "课程 " + cno + " 状态已切换" : "课程不存在: " + cno);
        return "redirect:/admin/courses";
    }

    @PostMapping("/{cno}/delete")
    public String delete(@PathVariable String cno, RedirectAttributes redirectAttributes) {
        List<String> errors = adminCourseService.delete(cno);
        if (errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "课程 " + cno + " 删除成功");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", String.join(" ", errors));
        }
        return "redirect:/admin/courses";
    }

    @GetMapping("/{cno}/students")
    public String roster(@PathVariable String cno, Model model,
                         RedirectAttributes redirectAttributes) {
        return courseQueryService.findForm(cno)
                .map(form -> {
                    model.addAttribute("cno", cno);
                    model.addAttribute("courseName", form.getName());
                    model.addAttribute("students", courseQueryService.listEnrolledStudents(cno));
                    return "admin/course-students";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "课程不存在: " + cno);
                    return "redirect:/admin/courses";
                });
    }

    private void addCourseOptions(Model model, String excludeCno) {
        var options = courseQueryService.listAll().stream()
                .filter(course -> excludeCno == null || !excludeCno.equals(course.getCno()))
                .toList();
        model.addAttribute("courseOptions", options);
        model.addAttribute("dayNames", List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日"));
    }
}
