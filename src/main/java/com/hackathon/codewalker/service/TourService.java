package com.hackathon.codewalker.service;

import com.hackathon.codewalker.model.TutorialTour;
import com.hackathon.codewalker.model.TutorialStep;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

// Scope: PROJECT means a new instance is created for every Project window the user opens.
@Service(Service.Level.PROJECT)
public final class TourService {
    private final Project project;
    private TutorialTour currentTour;

    public TourService(Project project) {
        this.project = project;
        this.currentTour = new TutorialTour("My New Tour"); // Default tour
    }

    // Helper to get this service from anywhere
    public static TourService getInstance(Project project) {
        return project.getService(TourService.class);
    }

    public void addStepToTour(TutorialStep step) {
        currentTour.addStep(step);
        System.out.println("Step added! Total steps: " + currentTour.getSteps().size());
        // TODO: Trigger UI update here later
    }

    public TutorialTour getCurrentTour() {
        return currentTour;
    }
}