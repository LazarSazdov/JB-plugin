package com.hackathon.actions;

import com.hackathon.service.SelectionModeService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Clears all current tour selections while in selection mode.
 */
public class ClearTourSelectionsAction extends AnAction {
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
            TourStateService state = project.getService(TourStateService.class);
            visible = sel != null && sel.isEnabled() && state != null && !state.getSteps().isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        TourStateService state = project.getService(TourStateService.class);
        state.clear();
        SelectionModeService sel = project.getService(SelectionModeService.class);
        if (sel != null) {
            // Refresh highlights in all editors
            for (Editor editor : com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors()) {
                sel.refreshEditorHighlighters(editor);
            }
        }
    }
}
