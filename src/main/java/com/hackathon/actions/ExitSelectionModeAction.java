package com.hackathon.actions;

import com.hackathon.service.SelectionModeService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ExitSelectionModeAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = false;
        if (project != null) {
            SelectionModeService sel = project.getService(SelectionModeService.class);
            visible = sel != null && sel.isEnabled();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        SelectionModeService sel = project.getService(SelectionModeService.class);
        if (sel != null) {
            sel.setEnabled(false);
        }
    }
}

