package com.hackathon.service;

import com.hackathon.model.TourStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level service that manages the "Create Tour" selection mode:
 * - Toggle selection mode on/off
 * - Listen to editor mouse clicks; when enabled, toggle selection of the enclosing function/class
 * - Maintain gutter index highlighters for all selections in the currently open editors
 */
@Service(Service.Level.PROJECT)
public final class SelectionModeService implements Disposable {
    private final Project project;
    private volatile boolean enabled;

    // Store editor-specific highlighters to clear on disable or editor disposal
    private final Map<Editor, List<RangeHighlighter>> editorHighlighters = new ConcurrentHashMap<>();

    public SelectionModeService(Project project) {
        this.project = project;

        // Attach a factory listener to register mouse listener for new editors
        com.intellij.openapi.editor.EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                editor.addEditorMouseListener(editorMouseListener);
                if (enabled) refreshEditorHighlighters(editor);
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                clearHighlighters(editor);
                editor.removeEditorMouseListener(editorMouseListener);
            }
        }, this);
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enable) {
        if (this.enabled == enable) return;
        this.enabled = enable;
        if (!enable) {
            clearAllHighlighters();
        } else {
            for (Editor editor : com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors()) {
                refreshEditorHighlighters(editor);
            }
        }
    }

    private final EditorMouseListener editorMouseListener = new EditorMouseListener() {
        @Override
        public void mouseClicked(@NotNull EditorMouseEvent e) {
            if (!enabled) return;
            Editor editor = e.getEditor();
            PsiFile psiFile = getPsiFile(editor);
            if (psiFile == null) return;

            int offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
            PsiElement target = findEnclosingSymbol(psiFile, offset);
            if (target == null) return;

            // Build TourStep
            Document doc = editor.getDocument();
            TextRange range = target.getTextRange();
            int startLine0 = doc.getLineNumber(range.getStartOffset());
            int endLine0 = doc.getLineNumber(range.getEndOffset());
            String code = doc.getText(new TextRange(doc.getLineStartOffset(startLine0), doc.getLineEndOffset(endLine0)));
            VirtualFile vFile = psiFile.getVirtualFile();
            if (vFile == null) return;

            String note = extractAuthorNote(target);
            String symbolName = computeSymbolName(target);

            TourStateService state = project.getService(TourStateService.class);

            // Toggle selection: if already present for same file+start line, remove it; else add it
            Optional<TourStep> existing = state.getSteps().stream()
                    .filter(s -> Objects.equals(s.filePath(), vFile.getPath()) && s.lineNum() == (startLine0 + 1) &&
                            Objects.equals(s.endLine(), endLine0 + 1))
                    .findFirst();
            if (existing.isPresent()) {
                removeStep(state, existing.get());
            } else {
                TourStep step = new TourStep(vFile.getPath(), startLine0 + 1, code, note, null, endLine0 + 1, symbolName);
                state.addStep(step);
            }

            refreshAllEditors();
        }
    };

    private void removeStep(TourStateService state, TourStep step) {
        // Internal mutation helper: not exposed on service, so rebuild list
        List<TourStep> copy = new ArrayList<>(state.getSteps());
        copy.remove(step);
        state.clear();
        for (TourStep s : copy) state.addStep(s);
    }

    private static PsiFile getPsiFile(Editor editor) {
        Project project = editor.getProject();
        if (project == null) return null;
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    }

    private static PsiElement findEnclosingSymbol(PsiFile file, int offset) {
        PsiElement el = file.findElementAt(offset);
        while (el != null && !(el instanceof PsiMethod) && !(el instanceof PsiClass)) {
            el = el.getParent();
        }
        return el;
    }

    private static String computeSymbolName(PsiElement el) {
        if (el instanceof PsiMethod m) {
            String cls = Optional.ofNullable(m.getContainingClass()).map(PsiClass::getQualifiedName).orElse("<local>");
            return cls + "#" + m.getName();
        } else if (el instanceof PsiClass c) {
            return Optional.ofNullable(c.getQualifiedName()).orElse(c.getName());
        }
        return null;
    }

    private static String extractAuthorNote(PsiElement el) {
        // Prefer Javadoc, then preceding comments on previous siblings
        if (el instanceof PsiDocCommentOwner owner) {
            PsiDocComment doc = owner.getDocComment();
            if (doc != null) {
                return doc.getText();
            }
        }
        PsiElement prev = el.getPrevSibling();
        while (prev != null && (prev instanceof PsiWhiteSpace)) prev = prev.getPrevSibling();
        if (prev instanceof PsiComment c) {
            return c.getText();
        }
        return "";
    }

    public void refreshEditorHighlighters(@NotNull Editor editor) {
        clearHighlighters(editor);
        if (!enabled) return;
        Project p = editor.getProject();
        if (p == null) return;
        PsiFile psi = getPsiFile(editor);
        if (psi == null || psi.getVirtualFile() == null) return;
        String path = psi.getVirtualFile().getPath();
        TourStateService state = project.getService(TourStateService.class);
        Document document = editor.getDocument();

        List<RangeHighlighter> list = new ArrayList<>();
        int index = 1;
        for (TourStep s : state.getSteps()) {
            if (!Objects.equals(s.filePath(), path)) { index++; continue; }
            int startLine0 = Math.max(0, Math.min(document.getLineCount() - 1, s.lineNum() - 1));
            int endLine0 = Math.max(startLine0, Math.min(document.getLineCount() - 1, s.endLine() != null ? s.endLine() - 1 : startLine0));
            int startOffset = document.getLineStartOffset(startLine0);
            int endOffset = document.getLineEndOffset(endLine0);

            TextAttributes attrs = new TextAttributes();
            attrs.setBackgroundColor(new JBColor(new Color(200, 225, 255), new Color(60, 70, 80))); // light blue / dark mode
            attrs.setEffectColor(JBColor.BLUE);
            attrs.setEffectType(EffectType.BOXED);

            MarkupModel markup = editor.getMarkupModel();
            RangeHighlighter rh = markup.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION - 2, attrs, HighlighterTargetArea.EXACT_RANGE);
            rh.setGutterIconRenderer(new NumberGutterIconRenderer(index));
            list.add(rh);
            index++;
        }
        editorHighlighters.put(editor, list);
    }

    /** Refresh selection highlighters and gutter numbers across all open editors. */
    public void refreshAllEditors() {
        for (Editor ed : com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors()) {
            refreshEditorHighlighters(ed);
        }
    }

    private void clearAllHighlighters() {
        for (Editor ed : new ArrayList<>(editorHighlighters.keySet())) {
            clearHighlighters(ed);
        }
    }

    private void clearHighlighters(Editor editor) {
        List<RangeHighlighter> list = editorHighlighters.remove(editor);
        if (list != null) {
            for (RangeHighlighter h : list) {
                try { h.dispose(); } catch (Throwable ignore) {}
            }
        }
    }

    @Override
    public void dispose() {
        clearAllHighlighters();
    }

    /** Simple numbered icon for gutter. */
    static final class NumberGutterIconRenderer extends GutterIconRenderer {
        private final int index;
        private final Icon icon;

        NumberGutterIconRenderer(int index) {
            this.index = index;
            this.icon = new NumberIcon(index);
        }

        @Override
        public @NotNull Icon getIcon() { return icon; }

        @Override
        public String getTooltipText() { return "Tour Step #" + index; }

        @Override
        public boolean equals(Object obj) { return obj instanceof NumberGutterIconRenderer r && r.index == index; }

        @Override
        public int hashCode() { return Integer.hashCode(index); }
    }

    /** Draws a blue circle badge with the step number. */
    static final class NumberIcon implements Icon {
        private final int number;
        private final int size = 16;
        NumberIcon(int number) { this.number = number; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new JBColor(new Color(0, 120, 215), new Color(0, 120, 215))); // Windows blue
            g2.fillOval(x, y, size, size);
            g2.setColor(JBColor.WHITE);
            String s = String.valueOf(number);
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(s)) / 2;
            int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(s, tx, ty);
            g2.dispose();
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
