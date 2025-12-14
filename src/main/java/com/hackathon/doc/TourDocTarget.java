package com.hackathon.doc;

import com.hackathon.model.TourStep;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple documentation target that displays the AI explanation for the current tour step.
 */
public class TourDocTarget implements DocumentationTarget {
    private final @Nullable PsiElement element;
    private final TourStep step;

    public TourDocTarget(@Nullable PsiElement element, @NotNull TourStep step) {
        this.element = element;
        this.step = step;
    }

    @Override
    public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
        // Stateless pointer: we don't need to persist across PSI changes for hackathon purposes.
        TourStep s = this.step;
        PsiElement el = this.element;
        return () -> new TourDocTarget(el, s);
    }

    @Override
    public @Nullable DocumentationResult computeDocumentation() {
        StringBuilder sb = new StringBuilder();
        String note = step.authorNote();
        if (note != null && !note.isBlank()) {
            sb.append("<h3>Author Note</h3><p>").append(note).append("</p>");
        }

        String ai = step.aiExplanation();
        if (ai != null && !ai.isBlank()) {
            sb.append(ai);
        }

        String html = sb.toString();
        if (html.isBlank()) {
            html = "<p><em>No explanation available.</em></p>";
        }
        return DocumentationResult.documentation(html);
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        String text = step.authorNote() != null && !step.authorNote().isBlank()
                ? step.authorNote()
                : "Tour Step (line " + step.lineNum() + ")";
        return TargetPresentation.builder(text).presentation();
    }




}