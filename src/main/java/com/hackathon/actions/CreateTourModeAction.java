package com.hackathon.actions;

import com.hackathon.service.SelectionModeService;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class CreateTourModeAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        Project project = e.getProject();
        boolean visible = false;
        if (editor != null && project != null) {
            SelectionModeService svc = project.getService(SelectionModeService.class);
            // Visible only if selection mode is NOT enabled
            visible = svc != null && !svc.isEnabled();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        SelectionModeService svc = project.getService(SelectionModeService.class);

        // End any running tour
        AnAction endTourAction = ActionManager.getInstance().getAction("com.hackathon.actions.EndTourAction");
        if (endTourAction != null) {
            endTourAction.actionPerformed(e);
        }

        svc.setEnabled(true);
        Messages.showInfoMessage(project,
                "Selection mode enabled. Click methods/classes to add or remove them from the tour. Use 'Create Tour (Generate JSON)' to finalize.",
                "Create Tour");
    }
}
