package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.hackathon.ui.TourOverlayManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class StartTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = false;
        if (project != null && project.getBasePath() != null) {
            File f = new File(project.getBasePath(), ".codewalker/tour.json");
            visible = f.exists();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        String base = project.getBasePath();
        if (base == null) {
            Messages.showErrorDialog(project, "No project base path.", "Start Tour");
            return;
        }
        File tourFile = new File(base, ".codewalker/tour.json");
        if (!tourFile.exists()) {
            Messages.showErrorDialog(project, "No tour found at .codewalker/tour.json", "Start Tour");
            return;
        }
        try (FileReader fr = new FileReader(tourFile, StandardCharsets.UTF_8)) {
            Tour tour = gson.fromJson(fr, Tour.class);
            if (tour == null || tour.steps() == null || tour.steps().isEmpty()) {
                Messages.showErrorDialog(project, "Invalid or empty tour.json.", "Start Tour");
                return;
            }
            TourStateService state = project.getService(TourStateService.class);
            state.setTour(tour);
            TourStep step = state.getCurrentStep();
            if (step == null) return;
            EditorNavigationService.navigateToStep(project, step);
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                TourOverlayManager.show(project, editor, step, state.getCurrentStepIndex() + 1, state.getSteps().size());
            }
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to load tour.json: " + ex.getMessage(), "Start Tour");
        }
    }
}
