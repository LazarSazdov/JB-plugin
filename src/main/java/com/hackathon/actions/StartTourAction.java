package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.hackathon.ui.TourToolWindow;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class StartTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = false;
        if (project != null && project.getBasePath() != null) {
            File f1 = new File(project.getBasePath(), "tour.json");
            File f2 = new File(project.getBasePath(), ".codewalker/tour.json");
            visible = f1.exists() || f2.exists();
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
        File tourFile = new File(base, "tour.json");
        if (!tourFile.exists()) {
            File alt = new File(base, ".codewalker/tour.json");
            if (alt.exists()) {
                tourFile = alt;
            } else {
                Messages.showErrorDialog(project, "No tour.json found in project root.", "Start Tour");
                return;
            }
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

            // Activate/show the right-side tool window and refresh its content
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Auto Code Walker");
            if (toolWindow != null) {
                toolWindow.activate(() -> {
                    var cm = toolWindow.getContentManager();
                    cm.removeAllContents(true);
                    TourToolWindow panel = new TourToolWindow(project);
                    Content content = ContentFactory.getInstance().createContent(panel.getComponent(), "Tour", false);
                    cm.addContent(content);
                }, true);
            } else {
                Messages.showWarningDialog(project, "Tool window 'Auto Code Walker' not available.", "Start Tour");
            }
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to load tour.json: " + ex.getMessage(), "Start Tour");
        }
    }
}
