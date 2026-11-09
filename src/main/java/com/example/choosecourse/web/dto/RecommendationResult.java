package com.example.choosecourse.web.dto;

import java.util.List;

public class RecommendationResult {

    private final List<CourseRow> courses;
    private final int selectedCredits;
    private final int recommendedCredits;
    private final int availableCredits;
    private final int maxCredits;

    public RecommendationResult(List<CourseRow> courses, int selectedCredits,
                                int recommendedCredits, int availableCredits,
                                int maxCredits) {
        this.courses = List.copyOf(courses);
        this.selectedCredits = selectedCredits;
        this.recommendedCredits = recommendedCredits;
        this.availableCredits = availableCredits;
        this.maxCredits = maxCredits;
    }

    public List<CourseRow> getCourses() {
        return courses;
    }

    public int getSelectedCredits() {
        return selectedCredits;
    }

    public int getRecommendedCredits() {
        return recommendedCredits;
    }

    public int getAvailableCredits() {
        return availableCredits;
    }

    public int getMaxCredits() {
        return maxCredits;
    }
}
