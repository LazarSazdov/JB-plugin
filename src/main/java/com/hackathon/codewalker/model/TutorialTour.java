package com.hackathon.codewalker.model;

import java.util.ArrayList;
import java.util.List;

// Using a Class here instead of Record to allow mutable list operations easily
public class TutorialTour {
    private String title;
    private List<TutorialStep> steps = new ArrayList<>();

    public TutorialTour(String title) {
        this.title = title;
    }

    public void addStep(TutorialStep step) {
        steps.add(step);
    }

    public List<TutorialStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TutorialStep> steps) {
        this.steps = steps;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}