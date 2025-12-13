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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
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
    private final Map<Editor, FinishHud> finishHuds = new ConcurrentHashMap<>();

    public SelectionModeService(Project project) {
        this.project = project;

        // Attach a factory listener to register mouse listener for new editors
        com.intellij.openapi.editor.EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                editor.addEditorMouseListener(editorMouseListener);
                if (enabled) {
                    refreshEditorHighlighters(editor);
                    attachFinishHud(editor);
                }
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                Editor editor = event.getEditor();
                clearHighlighters(editor);
                editor.removeEditorMouseListener(editorMouseListener);
                detachFinishHud(editor);
            }
        }, this);

        // Also attach listeners to already open editors (users may enable plugin mid-session)
        for (Editor ed : com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors()) {
            ed.addEditorMouseListener(editorMouseListener);
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enable) {
        if (this.enabled == enable) return;
        this.enabled = enable;
        if (!enable) {
            clearAllHighlighters();
            // remove HUDs
            for (Editor ed : new ArrayList<>(finishHuds.keySet())) {
                detachFinishHud(ed);
            }
        } else {
            for (Editor editor : com.intellij.openapi.editor.EditorFactory.getInstance().getAllEditors()) {
                refreshEditorHighlighters(editor);
                attachFinishHud(editor);
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
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) return null;

        PsiMethod outerMostMethod = null;
        PsiClass outerMostNonAnonClass = null;

        for (PsiElement cur = leaf; cur != null; cur = cur.getParent()) {
            if (cur instanceof PsiMethod m) {
                PsiClass cls = m.getContainingClass();
                boolean inAnonymous = cls instanceof PsiAnonymousClass;
                boolean methodInsideAnotherMethod = com.intellij.psi.util.PsiTreeUtil.getParentOfType(m, PsiMethod.class, true) != null;
                boolean classInsideMethod = cls != null && com.intellij.psi.util.PsiTreeUtil.getParentOfType(cls, PsiMethod.class, true) != null;
                // Skip overridden/inner methods declared inside anonymous or local classes within an outer method
                if (!inAnonymous && !methodInsideAnotherMethod && !classInsideMethod) {
                    outerMostMethod = m; // keep updating to capture the top-most method on the way up
                }
            } else if (cur instanceof PsiClass c) {
                if (!(c instanceof PsiAnonymousClass)) {
                    outerMostNonAnonClass = c; // keep updating to capture the outer-most class
                }
            }
        }

        if (outerMostMethod != null) return outerMostMethod;
        if (outerMostNonAnonClass != null) return outerMostNonAnonClass;

        // Fallback to nearest method/class (even if anonymous) to avoid nulls
        PsiElement el = leaf;
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
        while (prev instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
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
            boolean isMethod = s.symbolName() != null && s.symbolName().contains("#");
            // Softer, dimmer highlight colors; class=blue, method=green
            JBColor bg = isMethod ? new JBColor(new Color(210, 245, 210), new Color(50, 70, 50))
                                  : new JBColor(new Color(210, 230, 255), new Color(50, 60, 80));
            attrs.setBackgroundColor(bg);
            attrs.setEffectColor(isMethod ? new JBColor(new Color(0, 128, 0), new Color(120, 180, 120)) : JBColor.BLUE);
            attrs.setEffectType(EffectType.BOXED);
            attrs.setFontType(Font.BOLD);

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
        for (Editor ed : new ArrayList<>(finishHuds.keySet())) {
            detachFinishHud(ed);
        }
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

    // --- Finish HUD (blue "Finish Tour" button shown during selection mode) ---
    private void attachFinishHud(Editor editor) {
        if (editor == null || editor.getProject() == null) return;
        if (finishHuds.containsKey(editor)) return;
        FinishHud hud = new FinishHud(editor);
        finishHuds.put(editor, hud);
        hud.attach();
    }

    private void detachFinishHud(Editor editor) {
        FinishHud hud = finishHuds.remove(editor);
        if (hud != null) hud.detach();
    }

    private final class FinishHud extends JComponent {
        private final Editor editor;
        private final JButton finishBtn = new JButton("Finish Tour");
        private final java.awt.event.ComponentListener compListener = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { relayout(); }
            @Override public void componentMoved(java.awt.event.ComponentEvent e) { relayout(); }
        };
        private final com.intellij.openapi.editor.event.VisibleAreaListener visibleAreaListener = e -> relayout();

        FinishHud(Editor editor) {
            this.editor = editor;
            setLayout(null);
            setOpaque(false);
            finishBtn.setBackground(new JBColor(new Color(0, 120, 215), new Color(0, 90, 180)));
            finishBtn.setForeground(JBColor.WHITE);
            finishBtn.addActionListener(e -> performFinalize());
            add(finishBtn);
        }

        void attach() {
            JRootPane root = SwingUtilities.getRootPane(editor.getContentComponent());
            if (root == null) return;
            JLayeredPane layered = root.getLayeredPane();
            if (getParent() != layered) layered.add(this, JLayeredPane.POPUP_LAYER);
            relayout();
            editor.getContentComponent().addComponentListener(compListener);
            editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
        }

        void detach() {
            Container p = getParent();
            if (p != null) p.remove(this);
            try { editor.getContentComponent().removeComponentListener(compListener); } catch (Throwable ignore) {}
            try { editor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener); } catch (Throwable ignore) {}
        }

        private void relayout() {
            JRootPane root = SwingUtilities.getRootPane(editor.getContentComponent());
            if (root == null) return;
            JLayeredPane layered = root.getLayeredPane();
            Rectangle r = SwingUtilities.convertRectangle(editor.getContentComponent(), editor.getContentComponent().getBounds(), layered);
            setBounds(r);
            int btnW = 140, btnH = 30;
            finishBtn.setBounds(r.width - btnW - 20, 20, btnW, btnH);
            revalidate();
            repaint();
        }

        private void performFinalize() {
            // Invoke FinalizeTourAction programmatically in the context of the editor
            AnAction action = ActionManager.getInstance().getAction("com.hackathon.actions.FinalizeTourAction");
            if (action != null) {
                DataContext ctx = SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, project)
                        .add(CommonDataKeys.EDITOR, editor)
                        .build();
                AnActionEvent ev = AnActionEvent.createEvent(ctx, action.getTemplatePresentation(), "AutoCodeWalker.SelectionHud", ActionUiKind.NONE, null);
                //noinspection deprecation
                ActionUtil.performActionDumbAwareWithCallbacks(action, ev);
            }
        }
    }
}
