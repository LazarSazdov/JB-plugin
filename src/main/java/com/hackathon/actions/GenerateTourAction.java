package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class GenerateTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        TourStateService state = project.getService(TourStateService.class);
        if (state.getSteps().isEmpty()) {
            Messages.showInfoMessage(project, "No steps to export. Add steps from the editor.", "Generate Tour");
            return;
        }

        String title = Messages.showInputDialog(project, "Tour title:", "Generate Tour JSON", null, state.getTitle(), null);
        if (title == null) return;
        state.setTitle(title);

        File dir = new File(project.getBasePath(), ".codewalker");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Messages.showErrorDialog(project, "Failed to create .codewalker directory.", "Generate Tour");
                return;
            }
        }
        File file = new File(dir, "tour.json");

        Tour tour = new Tour(title, new ArrayList<>(state.getSteps()));
        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(tour, fw);
            Messages.showInfoMessage(project, "Tour saved to: " + file.getAbsolutePath(), "Generate Tour");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to save tour: " + ex.getMessage(), "Generate Tour");
        }
    }
}
