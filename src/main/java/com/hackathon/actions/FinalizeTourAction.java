package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.openai.OpenAIService;
import com.hackathon.service.SelectionModeService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a tour.json in the project root with AI explanations for selected symbols.
 */
public class FinalizeTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enable = false;
        if (project != null) {
            TourStateService state = project.getService(TourStateService.class);
            enable = state != null && !state.getSteps().isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(enable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        TourStateService state = project.getService(TourStateService.class);
        if (state.getSteps().isEmpty()) {
            Messages.showInfoMessage(project, "No selections. Use 'Create Tour (Select Functions)' first.", "Create Tour");
            return;
        }

        OpenAIService ai = com.intellij.openapi.application.ApplicationManager.getApplication().getService(OpenAIService.class);
        List<TourStep> updated = new ArrayList<>();
        String title = state.getTitle();
        for (TourStep s : state.getSteps()) {
            var result = ai.generateExplanation(s.codeSnippet(), s.authorNote() == null ? "" : s.authorNote());
            String html = result != null ? result.htmlContent() : s.aiExplanation();
            if (result != null && (title == null || title.isBlank() || "Untitled Tour".equals(title))) {
                title = result.title();
            }
            updated.add(new TourStep(s.filePath(), s.lineNum(), s.codeSnippet(), s.authorNote(), html, s.endLine(), s.symbolName()));
        }

        String suggested = title == null || title.isBlank() ? "Auto Code Walker Tour" : title;
        String input = Messages.showInputDialog(project, "Tour Title:", "Create Tour", null, suggested, null);
        if (input != null && !input.isBlank()) title = input.trim();
        state.setTour(new Tour(title, updated));

        String basePath = project.getBasePath();
        if (basePath == null) basePath = new File(".").getAbsolutePath();
        File out = new File(basePath, "tour.json");
        if (out.exists()) {
            int choice = Messages.showYesNoDialog(project,
                    "tour.json already exists in project root. Overwrite?",
                    "Create Tour",
                    "Overwrite",
                    "Cancel",
                    null);
            if (choice != Messages.YES) return;
        }
        try (FileWriter fw = new FileWriter(out, StandardCharsets.UTF_8)) {
            gson.toJson(new Tour(title, updated), fw);
            Messages.showInfoMessage(project, "Tour saved to: " + out.getAbsolutePath(), "Create Tour");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to save tour: " + ex.getMessage(), "Create Tour");
        }

        // Disable selection mode
        project.getService(SelectionModeService.class).setEnabled(false);
    }
}
