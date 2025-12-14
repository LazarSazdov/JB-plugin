package com.hackathon.ui;

import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a semi-transparent overlay over the active editor, with a focus rectangle
 * highlighting the function range and a floating panel showing explanation and a Next button.
 */
public final class TourOverlayManager {
    private static final Map<Editor, Overlay> overlays = new HashMap<>();

    private TourOverlayManager() {}

    public static void show(Project project, Editor editor, TourStep step, int index, int total) {
        if (editor == null || editor.getContentComponent() == null) return;
        Overlay ov = overlays.get(editor);
        if (ov == null) {
            ov = new Overlay(project, editor);
            overlays.put(editor, ov);
        }
        ov.setStep(step, index, total);
        ov.attach();
    }

    public static void hide(Editor editor) {
        Overlay ov = overlays.remove(editor);
        if (ov != null) ov.detach();
        EditorNavigationService.clearHighlight();
    }

    public static void hideAll() {
        for (Editor editor : new HashMap<>(overlays).keySet()) {
            hide(editor);
        }
    }

    static final class Overlay extends JComponent {
        private final Project project;
        private final Editor editor;
        private final JPanel infoPanel = new JPanel(new BorderLayout(8, 8));
        private final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        private final JButton nextButton = new JButton("Next");
        private final JButton prevButton = new JButton("Previous");
        private final JButton finishButton = new JButton("Finish Tour");
        private final JLabel stepLabel = new JLabel();
        private final JEditorPane html = new JEditorPane("text/html", "");
        private TourStep step;
        private int index;
        private int total;

        Overlay(Project project, Editor editor) {
            this.project = project;
            this.editor = editor;
            setOpaque(false);
            setFocusable(true);
            html.setEditable(false);

            // Info Panel (Explanation)
            infoPanel.setOpaque(true);
            // Make the info box darker than the background in both themes and use white text
            infoPanel.setBackground(new JBColor(new Color(40, 44, 52, 230), new Color(18, 18, 18, 230)));
            infoPanel.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(0,120,215), new Color(0,120,215))));
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(stepLabel, BorderLayout.WEST);
            JButton close = new JButton("Close");
            close.addActionListener(e -> TourOverlayManager.hide(editor));
            JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            topButtons.setOpaque(false);
            topButtons.add(close);
            top.add(topButtons, BorderLayout.EAST);
            stepLabel.setForeground(Color.WHITE);
            infoPanel.add(top, BorderLayout.NORTH);

            // Make HTML area inherit dark style (transparent components)
            html.setOpaque(false);
            JScrollPane scroller = new JScrollPane(html);
            scroller.setOpaque(false);
            if (scroller.getViewport() != null) scroller.getViewport().setOpaque(false);
            infoPanel.add(scroller, BorderLayout.CENTER);

            // Button Panel (Bottom Right Navigation)
            buttonPanel.setOpaque(false);
            buttonPanel.add(prevButton);
            buttonPanel.add(nextButton);
            buttonPanel.add(finishButton);

            // Styling
            JBColor blue = new JBColor(new Color(0, 120, 215), new Color(0, 120, 215));
            nextButton.setBackground(JBColor.BLUE);
            nextButton.setForeground(JBColor.WHITE);
            finishButton.setBackground(JBColor.BLUE);
            finishButton.setForeground(JBColor.WHITE);

            prevButton.setBackground(JBColor.GRAY);
            prevButton.setForeground(JBColor.WHITE);

            add(infoPanel);
            add(buttonPanel);

            nextButton.addActionListener(e -> doNext());
            prevButton.addActionListener(e -> doPrev());
            finishButton.addActionListener(e -> doClose());

            // Keyboard navigation & close
            registerKey("OVERLAY_CLOSE", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::doClose);
            registerKey("OVERLAY_NEXT_RIGHT", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), this::doNext);
            registerKey("OVERLAY_NEXT_ENTER", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), this::doNext);
            registerKey("OVERLAY_PREV_LEFT", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), this::doPrev);
        }

        void setStep(TourStep step, int index, int total) {
            this.step = step;
            this.index = index;
            this.total = total;
            String label = "Step " + index + " of " + total + (step.symbolName() != null ? ": " + step.symbolName() : "");
            stepLabel.setText(label);
            // Only show AI summary (sanitized) and/or author note. Never show code snippets.
            String ai = com.hackathon.util.HtmlSanitizer.stripCodeBlocks(step.aiExplanation());
            // Normalize any existing breaks before Example usage, then ensure double break is present
            if (ai != null) {
                // Normalize and ensure breaks before "Example usage:"
                ai = ai.replaceAll("(?i)(?:<br\\s*/?>\\s*){1,3}Example\\s+usage\\s*:", "Example usage:");
                ai = ai.replaceAll("(?i)Example\\s+usage\\s*:", "<br/><br/>Example usage:");
                // Normalize and ensure breaks before "Example call:"
                ai = ai.replaceAll("(?i)(?:<br\\s*/?>\\s*){1,3}Example\\s+call\\s*:", "Example call:");
                ai = ai.replaceAll("(?i)Example\\s+call\\s*:", "<br/><br/>Example call:");
            }
            StringBuilder sb = new StringBuilder("<html><head><style>"
                    + "body{color:#ffffff;background:transparent;font-family:sans-serif;font-size:13pt;}"
                    + "h1,h2,h3,h4,h5,h6{color:#ffffff;}"
                    + "p,li{color:#ffffff;}"
                    + "a{color:#61a8ff;}"
                    + "pre{display:none;}code{display:none;}"
                    + "</style></head><body>");
            if (step.authorNote() != null && !step.authorNote().isBlank()) {
                sb.append("<h3>Author Note</h3><p>").append(escape(step.authorNote())).append("</p>");
            }
            if (ai != null && !ai.isBlank()) {
                sb.append(ai);
            }
            sb.append("</body></html>");
            html.setText(sb.toString());
            html.setCaretPosition(0);
            // Show Finish on last step
            boolean isLast = index >= total;
            nextButton.setVisible(!isLast);
            finishButton.setVisible(isLast);
        }

        // Listeners to keep overlay in sync with editor viewport/size
        private final com.intellij.openapi.editor.event.VisibleAreaListener visibleAreaListener = e -> {
            revalidate();
            repaint();
        };
        private final java.awt.event.ComponentListener componentListener = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { revalidate(); repaint(); }
            @Override public void componentMoved(java.awt.event.ComponentEvent e) { revalidate(); repaint(); }
        };

        void attach() {
            JRootPane root = SwingUtilities.getRootPane(editor.getContentComponent());
            if (root == null) return;
            JLayeredPane layered = root.getLayeredPane();
            if (getParent() != layered) {
                layered.add(this, JLayeredPane.DRAG_LAYER);
            }
            //updateBounds(layered);
            setVisible(true);
            requestFocusInWindow();

            // Register listeners
            editor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
            editor.getContentComponent().addComponentListener(componentListener);
        }

        void detach() {
            Container p = getParent();
            if (p != null) p.remove(this);
            // Unregister listeners
            try { editor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener); } catch (Throwable ignore) {}
            try { editor.getContentComponent().removeComponentListener(componentListener); } catch (Throwable ignore) {}
        }

        private void updateBounds(JLayeredPane layered) {
            Rectangle r = SwingUtilities.convertRectangle(editor.getContentComponent(), editor.getContentComponent().getBounds(), layered);
            setBounds(r);
            // Compute focus rect vertical position to place panel near the explained code
            int startLine0 = step != null ? Math.max(0, step.lineNum() - 1) : 0;
            int endLine0 = step != null && step.endLine() != null ? Math.max(startLine0, step.endLine() - 1) : startLine0;
            Point p1 = editor.logicalPositionToXY(new LogicalPosition(startLine0, 0));
            Point p2 = editor.logicalPositionToXY(new LogicalPosition(endLine0 + 1, 0));
            int visY = editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
            int focusY = Math.max(0, p1.y - visY - 4);
            int focusH = Math.max(20, p2.y - p1.y + 8);

            int panelW = Math.min(450, r.width - 40);
            int panelH = Math.min(260, r.height - 40);
            int y = Math.max(20, Math.min(r.height - panelH - 20, focusY));
            // Prefer to the right side as a floating textbox near the focus lines
            infoPanel.setBounds(r.width - panelW - 20, y, panelW, panelH);

            // Position button panel at bottom right
            int btnW = 300;
            int btnH = 40;
            buttonPanel.setBounds(r.width - btnW - 20, r.height - btnH - 20, btnW, btnH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Dim background
            //g2.setColor(new JBColor(new Color(0, 0, 0, 100), new Color(0, 0, 0, 100)));
            //g2.fillRect(0, 0, getWidth(), getHeight());

            // Focus rectangle around selected lines
            /*int startLine0 = Math.max(0, step.lineNum() - 1);
            int endLine0 = step.endLine() != null ? Math.max(startLine0, step.endLine() - 1) : startLine0;
            Point p1 = editor.logicalPositionToXY(new LogicalPosition(startLine0, 0));
            Point p2 = editor.logicalPositionToXY(new LogicalPosition(endLine0 + 1, 0));
            int y = p1.y - editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y; // relative to content
            int y2 = p2.y - editor.getScrollingModel().getVisibleAreaOnScrollingFinished().y;
            int focusY = Math.max(0, y - 4);
            int focusH = Math.min(getHeight(), Math.max(20, y2 - y + 8));
            g2.setColor(new JBColor(new Color(255, 255, 255, 40), new Color(255, 255, 255, 40)));
            g2.fillRoundRect(8, focusY, getWidth() - 16, focusH, 8, 8);
            g2.setColor(new JBColor(new Color(0,120,215), new Color(0,120,215)));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(8, focusY, getWidth() - 16, focusH, 8, 8);*/
            g2.dispose();
            // Ensure info panel stays on top
            infoPanel.repaint();
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private void registerKey(String name, KeyStroke stroke, Runnable action) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
            getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            });
        }

        private void doClose() {
            TourOverlayManager.hide(editor);
        }

        private void doNext() {
            TourStateService state = project.getService(TourStateService.class);
            var next = state.nextStep();
            if (next == null) {
                TourOverlayManager.hide(editor);
                return;
            }
            EditorNavigationService.navigateToStep(project, next);
            // If navigation switched to another editor, migrate overlay
            Editor newEditor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (newEditor != null && newEditor != this.editor) {
                TourOverlayManager.hide(this.editor);
                TourOverlayManager.show(project, newEditor, next, state.getCurrentStepIndex() + 1, state.getSteps().size());
                return;
            }
            setStep(next, state.getCurrentStepIndex() + 1, state.getSteps().size());
            revalidate();
            repaint();
        }

        private void doPrev() {
            TourStateService state = project.getService(TourStateService.class);
            var prev = state.prevStep();
            if (prev == null) {
                return;
            }
            EditorNavigationService.navigateToStep(project, prev);
            Editor newEditor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (newEditor != null && newEditor != this.editor) {
                TourOverlayManager.hide(this.editor);
                TourOverlayManager.show(project, newEditor, prev, state.getCurrentStepIndex() + 1, state.getSteps().size());
                return;
            }
            setStep(prev, state.getCurrentStepIndex() + 1, state.getSteps().size());
            revalidate();
            repaint();
        }
    }
}
