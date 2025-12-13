package com.hackathon.doc;

import com.hackathon.model.TourStep;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TourDocTargetProvider implements DocumentationTargetProvider {
    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        Project project = file.getProject();
        TourStateService state = project.getService(TourStateService.class);
        List<TourStep> steps = state.getSteps();
        if (steps.isEmpty()) return Collections.emptyList();

        Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
        if (doc == null) return Collections.emptyList();

        int line = doc.getLineNumber(offset) + 1;
        String currentPath = file.getVirtualFile() != null ? file.getVirtualFile().getPath() : null;
        if (currentPath == null) return Collections.emptyList();

        for (TourStep step : steps) {
            int start = step.lineNum();
            int end = step.endLine() != null ? step.endLine() : start;
            if (line >= start && line <= end && currentPath.equals(step.filePath())) {
                PsiElement element = file.findElementAt(offset);
                return List.of(new TourDocTarget(element, step));
            }
        }
        return Collections.emptyList();
    }
}
