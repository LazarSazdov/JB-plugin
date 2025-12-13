package com.hackathon.actions;

import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.SelectionModeService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class EndTourAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = false;
        if (project != null) {
            // Hide if selection mode is active
            SelectionModeService sel = project.getService(SelectionModeService.class);
            if (sel != null && sel.isEnabled()) {
                e.getPresentation().setEnabledAndVisible(false);
                return;
            }

            TourStateService state = project.getService(TourStateService.class);
            // Visible if tour has steps (is loaded)
            visible = state != null && !state.getSteps().isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        TourStateService state = project.getService(TourStateService.class);
        state.clear(); // Clear steps and index

        EditorNavigationService.clearHighlight();

        // Hide tool window
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Auto Code Walker");
        if (toolWindow != null) {
            toolWindow.hide(null);
        }
    }
}

