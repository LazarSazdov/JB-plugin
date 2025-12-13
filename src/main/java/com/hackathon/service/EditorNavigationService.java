package com.hackathon.service;

import com.hackathon.model.TourStep;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class EditorNavigationService {
    private EditorNavigationService() {}

    public static void navigateToStep(Project project, TourStep step) {
        if (project == null || step == null) return;
        VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(step.filePath());
        if (vFile == null) return;

        // Open file and get editor
        Editor editor = FileEditorManager.getInstance(project)
                .openTextEditor(new OpenFileDescriptor(project, vFile, Math.max(step.lineNum() - 1, 0), 0), true);
        if (editor == null) return;

        Document document = editor.getDocument();
        int startLine = Math.max(Math.min(step.lineNum() - 1, document.getLineCount() - 1), 0);
        int endLine = startLine;
        if (step.endLine() != null) {
            endLine = Math.max(Math.min(step.endLine() - 1, document.getLineCount() - 1), startLine);
        }
        LogicalPosition pos = new LogicalPosition(startLine, 0);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getScrollingModel().scrollTo(pos, ScrollType.CENTER);

        // Highlight the line
        int startOffset = document.getLineStartOffset(startLine);
        int endOffset = document.getLineEndOffset(endLine);

        TextAttributes attrs = new TextAttributes();
        attrs.setBackgroundColor(new Color(255, 255, 150)); // light yellow
        attrs.setEffectColor(new Color(255, 255, 0));
        attrs.setEffectType(EffectType.BOXED);

        MarkupModel markup = editor.getMarkupModel();
        // Remove previous highlighters we created (optional improvement: track ours). For simplicity, skip removal.
        markup.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SELECTION - 1,
                attrs,
                HighlighterTargetArea.EXACT_RANGE
        );
    }
}
