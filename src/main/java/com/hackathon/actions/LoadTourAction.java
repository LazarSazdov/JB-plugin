package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class LoadTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Tour JSON");
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file == null || !file.exists()) return;

        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            Tour tour = gson.fromJson(fr, Tour.class);
            if (tour == null) {
                Messages.showErrorDialog(project, "Invalid tour JSON.", "Load Tour");
                return;
            }
            TourStateService state = project.getService(TourStateService.class);
            state.setTour(tour);
            TourStep current = state.getCurrentStep();
            if (current != null) {
                EditorNavigationService.navigateToStep(project, current);
                Messages.showInfoMessage(project, "Loaded tour '" + tour.title() + "' with " + tour.steps().size() + " steps.", "Load Tour");
            }
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to load tour: " + ex.getMessage(), "Load Tour");
        }
    }
}
