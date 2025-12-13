package com.hackathon.actions;

import com.hackathon.model.TourStep;
import com.hackathon.openai.OpenAIService;
import com.hackathon.service.TourStateService;
import com.hackathon.ui.StepCreationDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AddStepAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean visible = false;
        if (editor != null && editor.getProject() != null) {
            com.hackathon.service.SelectionModeService sel = editor.getProject().getService(com.hackathon.service.SelectionModeService.class);
            visible = sel != null && sel.isEnabled();
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) return;

        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile == null) return;

        Document document = editor.getDocument();
        SelectionModel sel = editor.getSelectionModel();
        CaretModel caret = editor.getCaretModel();
        int start;
        int end;
        String code;
        if (sel.hasSelection()) {
            start = sel.getSelectionStart();
            end = sel.getSelectionEnd();
            code = sel.getSelectedText();
            if (code == null) code = "";
        } else {
            int offset = caret.getOffset();
            int line = document.getLineNumber(offset);
            start = document.getLineStartOffset(line);
            end = document.getLineEndOffset(line);
            code = document.getText().substring(start, end);
        }

        int lineNum = document.getLineNumber(start) + 1;
        int endLineNum = document.getLineNumber(end) + 1;

        StepCreationDialog dialog = new StepCreationDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }
        String note = dialog.getNote();

        OpenAIService ai = com.intellij.openapi.application.ApplicationManager.getApplication().getService(OpenAIService.class);
        var result = ai.generateExplanation(code, note);
        String explanation = result != null ? result.htmlContent() : null;

        TourStep step = new TourStep(vFile.getPath(), lineNum, code, note, explanation, endLineNum, null, "manual");

        TourStateService state = project.getService(TourStateService.class);
        state.addStep(step);
        if (result != null && state.getSteps().size() == 1) {
            state.setTitle(result.title());
        }
    }
}
