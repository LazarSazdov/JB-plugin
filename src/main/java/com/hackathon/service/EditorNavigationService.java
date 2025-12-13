package com.hackathon.service;

import com.hackathon.model.TourStep;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;

import java.awt.*;

public final class EditorNavigationService {
    private static RangeHighlighter lastHighlighter;
    private static Editor lastEditor;

    private EditorNavigationService() {}

    public static void navigateToStep(Project project, TourStep step) {
        if (project == null || step == null) return;

        // Clear previous highlight
        if (lastHighlighter != null && lastEditor != null && !lastEditor.isDisposed()) {
            lastEditor.getMarkupModel().removeHighlighter(lastHighlighter);
            lastHighlighter = null;
            lastEditor = null;
        }

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

        JBColor bg;
        JBColor effect;
        String type = step.type();
        if ("class".equals(type)) {
            // Blue
            bg = new JBColor(new Color(210, 230, 255), new Color(50, 60, 80));
            effect = JBColor.BLUE;
        } else if ("method".equals(type)) {
            // Green
            bg = new JBColor(new Color(210, 245, 210), new Color(50, 70, 50));
            effect = new JBColor(new Color(0, 128, 0), new Color(120, 180, 120));
        } else {
            // Manual / Random -> Yellow
            bg = new JBColor(new Color(255, 255, 200), new Color(80, 80, 40));
            effect = new JBColor(new Color(200, 200, 0), new Color(180, 180, 0));
        }

        attrs.setBackgroundColor(bg);
        attrs.setEffectColor(effect);
        attrs.setEffectType(EffectType.BOXED);
        attrs.setFontType(Font.BOLD);



        MarkupModel markup = editor.getMarkupModel();
        lastHighlighter = markup.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SELECTION - 1,
                attrs,
                HighlighterTargetArea.EXACT_RANGE
        );
        lastEditor = editor;
    }

    public static void clearHighlight() {
        if (lastHighlighter != null && lastEditor != null && !lastEditor.isDisposed()) {
            lastEditor.getMarkupModel().removeHighlighter(lastHighlighter);
        }
        lastHighlighter = null;
        lastEditor = null;
    }
}
