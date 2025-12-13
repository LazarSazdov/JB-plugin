package com.hackathon.doc;

import com.hackathon.model.TourStep;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TourDocTargetProvider implements PsiDocumentationTargetProvider {
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, @NotNull Editor editor, int offset) {
        Project project = file.getProject();
        TourStateService state = project.getService(TourStateService.class);
        TourStep step = state.getCurrentStep();
        if (step == null) return Collections.emptyList();

        Document doc = editor.getDocument();
        int line = doc.getLineNumber(offset) + 1;
        String currentPath = file.getVirtualFile() != null ? file.getVirtualFile().getPath() : null;
        if (currentPath == null) return Collections.emptyList();

        if (line == step.lineNum() && currentPath.equals(step.filePath())) {
            PsiElement element = file.findElementAt(offset);
            return List.of(new TourDocTarget(element, step));
        }
        return Collections.emptyList();
    }
}
