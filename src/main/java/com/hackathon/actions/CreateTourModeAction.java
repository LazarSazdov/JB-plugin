package com.hackathon.actions;

import com.hackathon.service.SelectionModeService;
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
        e.getPresentation().setEnabledAndVisible(editor != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        SelectionModeService svc = project.getService(SelectionModeService.class);
        boolean enable = !svc.isEnabled();
        svc.setEnabled(enable);
        Messages.showInfoMessage(project,
                enable ? "Selection mode enabled. Click methods/classes to add or remove them from the tour. Use 'Create Tour (Generate JSON)' to finalize." : "Selection mode disabled.",
                "Create Tour");
    }
}
