package com.example.choosecourse.web;

import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.EnrollmentService;
import com.example.choosecourse.service.ScheduleIcsService;
import com.example.choosecourse.service.ScheduleService;
import com.example.choosecourse.service.StudentAccountService;
import com.example.choosecourse.service.StudentQueryService;
import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.EnrollmentOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final CourseQueryService courseQueryService;
    private final EnrollmentService enrollmentService;
    private final StudentQueryService studentQueryService;
    private final StudentAccountService studentAccountService;
    private final ScheduleService scheduleService;
    private final ScheduleIcsService scheduleIcsService;
    private final int maxCredits;
    private final int creditGoal;
    private final int pageSize;

    public StudentController(CourseQueryService courseQueryService,
                             EnrollmentService enrollmentService,
                             StudentQueryService studentQueryService,
                             StudentAccountService studentAccountService,
                             ScheduleService scheduleService,
                             ScheduleIcsService scheduleIcsService,
                             @Value("${app.max-credits:30}") int maxCredits,
                             @Value("${app.credit-goal:20}") int creditGoal,
                             @Value("${app.page-size:20}") int pageSize) {
        this.courseQueryService = courseQueryService;
        this.enrollmentService = enrollmentService;
        this.studentQueryService = studentQueryService;
        this.studentAccountService = studentAccountService;
        this.scheduleService = scheduleService;
        this.scheduleIcsService = scheduleIcsService;
        this.maxCredits = maxCredits;
        this.creditGoal = creditGoal;
        this.pageSize = pageSize;
    }

    @GetMapping("/courses")
    public String courses(Authentication authentication,
                          @RequestParam(name = "page", defaultValue = "1") int page,
                          @RequestParam(name = "q", required = false) String q,
                          @RequestParam(name = "available", defaultValue = "false") boolean available,
                          @RequestParam(name = "noConflict", defaultValue = "false") boolean noConflict,
                          Model model) {
        var coursePage = courseQueryService.pageForStudent(
                authentication.getName(), q, available, noConflict, page, pageSize);
        var selectedCourses = courseQueryService.listSelected(authentication.getName());
        model.addAttribute("courses", coursePage.getItems());
        model.addAttribute("coursePage", coursePage);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("available", available);
        model.addAttribute("noConflict", noConflict);
        model.addAttribute("selectedCourses", selectedCourses);
        model.addAttribute("selectedCredits", selectedCourses.stream()
                .mapToInt(CourseRow::getCredits)
                .sum());
        model.addAttribute("maxCredits", maxCredits);
        model.addAttribute("creditGoal", creditGoal);
        model.addAttribute("sessionsJson", courseQueryService.sessionJson());
        return "student/courses";
    }

    @PostMapping("/enroll")
    public String enroll(Authentication authentication,
                         @RequestParam(name = "cno", required = false) List<String> cnos,
                         RedirectAttributes redirectAttributes) {
        if (cnos == null || cnos.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "请先勾选要选的课程。");
            return "redirect:/student/courses";
        }

        StringBuilder message = new StringBuilder();
        for (String rawCno : cnos) {
            if (rawCno == null || rawCno.isBlank()) {
                continue;
            }
            String cno = rawCno.trim();
            EnrollmentOutcome outcome = enrollmentService.enroll(authentication.getName(), cno);
            message.append(cno).append("：").append(outcome.getMessage()).append('\n');
        }
        redirectAttributes.addFlashAttribute("message", message.toString());
        return "redirect:/student/courses";
    }

    @PostMapping("/withdraw")
    public String withdraw(Authentication authentication,
                           @RequestParam(name = "cno", required = false) List<String> cnos,
                           RedirectAttributes redirectAttributes) {
        if (cnos == null || cnos.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "请先勾选要退的课程。");
            return "redirect:/student/courses";
        }

        StringBuilder message = new StringBuilder();
        for (String rawCno : cnos) {
            if (rawCno == null || rawCno.isBlank()) {
                continue;
            }
            String cno = rawCno.trim();
            boolean removed = enrollmentService.withdraw(authentication.getName(), cno);
            message.append(cno).append("：").append(removed ? "退课成功" : "不在您的课表中").append('\n');
        }
        redirectAttributes.addFlashAttribute("message", message.toString());
        return "redirect:/student/courses";
    }

    @GetMapping("/schedule")
    public String schedule(Authentication authentication, Model model) {
        var entries = courseQueryService.listSchedule(authentication.getName());
        var selectedCourses = courseQueryService.listSelected(authentication.getName());

        model.addAttribute("blocks", scheduleService.buildBlocks(entries));
        model.addAttribute("hasSchedule", !entries.isEmpty());
        model.addAttribute("dayLabels", List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日"));
        model.addAttribute("periodLabels", List.of(
                "第一节", "第二节", "第三节", "第四节", "第五节", "第六节",
                "第七节", "第八节", "第九节", "第十节", "第十一节", "第十二节"));
        model.addAttribute("selectedCredits", selectedCourses.stream()
                .mapToInt(CourseRow::getCredits)
                .sum());
        return "student/schedule";
    }

    @GetMapping(value = "/schedule.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> scheduleIcs(Authentication authentication) {
        var entries = courseQueryService.listSchedule(authentication.getName());
        String ics = scheduleIcsService.build(authentication.getName(), entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule-" + authentication.getName() + ".ics\"")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(ics);
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        return studentQueryService.findBySno(authentication.getName())
                .map(student -> {
                    model.addAttribute("student", student);
                    return "student/profile";
                })
                .orElse("redirect:/login");
    }

    @GetMapping("/password")
    public String passwordForm() {
        return "student/password";
    }

    @PostMapping("/password")
    public String changePassword(Authentication authentication,
                                 @RequestParam(name = "oldPassword") String oldPassword,
                                 @RequestParam(name = "newPassword") String newPassword,
                                 @RequestParam(name = "confirmPassword") String confirmPassword,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        List<String> errors = studentAccountService.changePassword(
                authentication.getName(), oldPassword, newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            model.addAttribute("error", String.join("；", errors));
            return "student/password";
        }
        redirectAttributes.addFlashAttribute("message", "密码修改成功。");
        return "redirect:/student/profile";
    }

    @GetMapping("/phone")
    public String phoneForm(Authentication authentication, Model model) {
        studentQueryService.findBySno(authentication.getName())
                .ifPresent(student -> model.addAttribute("student", student));
        return "student/phone";
    }

    @PostMapping("/phone")
    public String changePhone(Authentication authentication,
                              @RequestParam(name = "password") String password,
                              @RequestParam(name = "newPhone") String newPhone,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        List<String> errors = studentAccountService.changePhone(
                authentication.getName(), password, newPhone);
        if (!errors.isEmpty()) {
            model.addAttribute("error", String.join("；", errors));
            studentQueryService.findBySno(authentication.getName())
                    .ifPresent(student -> model.addAttribute("student", student));
            return "student/phone";
        }
        redirectAttributes.addFlashAttribute("message", "密保手机修改成功。");
        return "redirect:/student/profile";
    }
}
