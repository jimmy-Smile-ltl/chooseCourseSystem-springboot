package com.example.choosecourse.web;

import com.example.choosecourse.service.AdminStudentService;
import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.StudentQueryService;
import com.example.choosecourse.web.form.StudentForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/students")
public class AdminStudentController {

    private final StudentQueryService studentQueryService;
    private final CourseQueryService courseQueryService;
    private final AdminStudentService adminStudentService;

    public AdminStudentController(StudentQueryService studentQueryService,
                                  CourseQueryService courseQueryService,
                                  AdminStudentService adminStudentService) {
        this.studentQueryService = studentQueryService;
        this.courseQueryService = courseQueryService;
        this.adminStudentService = adminStudentService;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String q, Model model) {
        model.addAttribute("students", studentQueryService.search(q));
        model.addAttribute("q", q == null ? "" : q);
        return "admin/students";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("studentForm", new StudentForm());
        model.addAttribute("editing", false);
        model.addAttribute("formAction", "/admin/students");
        addCourseOptions(model);
        return "admin/student-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("studentForm") StudentForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            model.addAttribute("formAction", "/admin/students");
            addCourseOptions(model);
            return "admin/student-form";
        }

        List<String> errors = adminStudentService.create(form);
        if (!errors.isEmpty()) {
            model.addAttribute("serviceErrors", errors);
            model.addAttribute("editing", false);
            model.addAttribute("formAction", "/admin/students");
            addCourseOptions(model);
            return "admin/student-form";
        }

        redirectAttributes.addFlashAttribute("message", "学生 " + form.getSno().trim() + " 添加成功");
        return "redirect:/admin/students";
    }

    @GetMapping("/{sno}/edit")
    public String editForm(@PathVariable String sno, Model model,
                           RedirectAttributes redirectAttributes) {
        return studentQueryService.findForm(sno)
                .map(form -> {
                    model.addAttribute("studentForm", form);
                    model.addAttribute("editing", true);
                    model.addAttribute("formAction", "/admin/students/" + sno);
                    addCourseOptions(model);
                    return "admin/student-form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "学生不存在: " + sno);
                    return "redirect:/admin/students";
                });
    }

    @PostMapping("/{sno}")
    public String update(@PathVariable String sno,
                         @Valid @ModelAttribute("studentForm") StudentForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        form.setSno(sno);
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", true);
            model.addAttribute("formAction", "/admin/students/" + sno);
            addCourseOptions(model);
            return "admin/student-form";
        }

        List<String> errors = adminStudentService.update(sno, form);
        if (!errors.isEmpty()) {
            model.addAttribute("serviceErrors", errors);
            model.addAttribute("editing", true);
            model.addAttribute("formAction", "/admin/students/" + sno);
            addCourseOptions(model);
            return "admin/student-form";
        }

        redirectAttributes.addFlashAttribute("message", "学生 " + sno + " 修改成功");
        return "redirect:/admin/students";
    }

    @PostMapping("/{sno}/delete")
    public String delete(@PathVariable String sno, RedirectAttributes redirectAttributes) {
        List<String> errors = adminStudentService.delete(sno);
        if (errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "学生 " + sno + " 删除成功");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", String.join(" ", errors));
        }
        return "redirect:/admin/students";
    }

    @GetMapping("/{sno}/courses")
    public String courses(@PathVariable String sno, Model model,
                          RedirectAttributes redirectAttributes) {
        return studentQueryService.findBySno(sno)
                .map(student -> {
                    model.addAttribute("student", student);
                    model.addAttribute("courses", courseQueryService.listForStudent(sno).stream()
                            .filter(course -> course.isSelected())
                            .toList());
                    return "admin/student-courses";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "学生不存在: " + sno);
                    return "redirect:/admin/students";
                });
    }

    private void addCourseOptions(Model model) {
        model.addAttribute("courseOptions", courseQueryService.listAll());
    }
}
