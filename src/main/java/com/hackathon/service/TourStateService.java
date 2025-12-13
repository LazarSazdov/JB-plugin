package com.hackathon.service;

import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class TourStateService {
    private final Project project;
    private final List<TourStep> steps = new ArrayList<>();
    private int currentStepIndex = -1;
    private String title = "Untitled Tour";

    public TourStateService(Project project) {
        this.project = project;
    }

    public void clear() {
        steps.clear();
        currentStepIndex = -1;
    }

    public void setTour(Tour tour) {
        clear();
        if (tour != null && tour.steps() != null) {
            steps.addAll(tour.steps());
            title = tour.title();
            if (!steps.isEmpty()) currentStepIndex = 0;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title != null && !title.isBlank()) this.title = title;
    }

    public List<TourStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public void addStep(TourStep step) {
        steps.add(step);
        if (currentStepIndex < 0) currentStepIndex = 0;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int idx) {
        if (idx >= 0 && idx < steps.size()) currentStepIndex = idx;
    }

    @Nullable
    public TourStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    @Nullable
    public TourStep nextStep() {
        if (currentStepIndex + 1 < steps.size()) {
            currentStepIndex++;
            return steps.get(currentStepIndex);
        }
        return null;
    }

    @Nullable
    public TourStep prevStep() {
        if (currentStepIndex - 1 >= 0) {
            currentStepIndex--;
            return steps.get(currentStepIndex);
        }
        return null;
    }
}
