package com.hackathon.codewalker.actions;

import com.hackathon.codewalker.model.TutorialStep;
import com.hackathon.codewalker.service.TourService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class AddStepAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 1. Get Context (Project, Editor, File)
        Project project = e.getData(CommonDataKeys.PROJECT);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || editor == null || psiFile == null || psiFile.getVirtualFile() == null) return;

        //  Find the Code Element under the Caret
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);

        //  Walk up the tree to find the parent Method
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

        if (method == null) {
            Messages.showErrorDialog(project, "Please click inside a Java method.", "No Method Found");
            return;
        }

        // Extract Data (FQN = Fully Qualified Name)
        String className = method.getContainingClass() != null ? method.getContainingClass().getQualifiedName() : "Unknown";
        String methodName = method.getName();
        String fqn = className + "." + methodName;
        int lineNumber = editor.getDocument().getLineNumber(offset);

        // Ask User for a Note (Simple Input Dialog)
        String userNote = Messages.showInputDialog(project,
                "Enter a note for this step:",
                "Add Step to Tour",
                Messages.getQuestionIcon());

        if (userNote == null) return; // User cancelled

        // Create the Step and Save it
        TutorialStep step = new TutorialStep(
                psiFile.getVirtualFile().getPath(), // File Path
                fqn,                                // FQN (Anchor)
                lineNumber,                         // Fallback Line
                userNote,                           // Author Note
                "Pending AI..."                     // AI Explanation (filled later)
        );

        TourService.getInstance(project).addStepToTour(step);
    }
}