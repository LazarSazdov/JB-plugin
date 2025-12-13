package com.hackathon.ui;

import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class TourToolWindow {
    private final Project project;
    private final JPanel root;
    private final JLabel titleLabel;
    private final JEditorPane htmlPane;
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JButton finishBtn;

    public TourToolWindow(Project project) {
        this.project = project;
        this.root = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new BorderLayout());
        titleLabel = new JLabel("Auto Code Walker");
        top.add(titleLabel, BorderLayout.WEST);
        root.add(top, BorderLayout.NORTH);

        htmlPane = new JEditorPane();
        htmlPane.setContentType("text/html");
        htmlPane.setEditable(false);
        root.add(new JScrollPane(htmlPane), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        prevBtn = new JButton("Prev");
        nextBtn = new JButton("Next");
        finishBtn = new JButton("Finish Tour");
        controls.add(prevBtn);
        controls.add(nextBtn);
        controls.add(finishBtn);
        root.add(controls, BorderLayout.SOUTH);

        prevBtn.addActionListener(e -> {
            TourStateService state = project.getService(TourStateService.class);
            TourStep step = state.prevStep();
            if (step != null) {
                updateHtml(step);
                EditorNavigationService.navigateToStep(project, step);
            }
        });
        nextBtn.addActionListener(e -> {
            TourStateService state = project.getService(TourStateService.class);
            TourStep step = state.nextStep();
            if (step != null) {
                updateHtml(step);
                EditorNavigationService.navigateToStep(project, step);
            }
        });

        finishBtn.addActionListener(e -> finishTour());

        refresh();
    }

    private void refresh() {
        TourStateService state = project.getService(TourStateService.class);
        titleLabel.setText("Auto Code Walker: " + state.getTitle());
        TourStep current = state.getCurrentStep();
        if (current != null) {
            updateHtml(current);
        } else {
            htmlPane.setText("<html><body><p>No steps yet. Use editor popup: Add Tour Step.</p></body></html>");
            finishBtn.setVisible(false);
            nextBtn.setVisible(true);
        }
    }

    private void updateHtml(TourStep step) {
        TourStateService state = project.getService(TourStateService.class);
        titleLabel.setText("Auto Code Walker: " + state.getTitle());
        String html = step.aiExplanation() != null && !step.aiExplanation().isBlank()
                ? step.aiExplanation()
                : ("<h3>" + step.authorNote() + "</h3><pre>" + escape(step.codeSnippet()) + "</pre>");
        htmlPane.setText("<html><body>" + html + "</body></html>");
        htmlPane.setCaretPosition(0);

        // Show Finish only on last step
        int idx = state.getCurrentStepIndex();
        int total = state.getSteps().size();
        boolean isLast = idx == total - 1;
        finishBtn.setVisible(isLast);
        nextBtn.setVisible(!isLast);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public JComponent getComponent() {
        return root;
    }

    private void finishTour() {
        // Optionally hide the tool window after finishing
        javax.swing.SwingUtilities.invokeLater(() -> {
            com.intellij.openapi.wm.ToolWindow tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Auto Code Walker");
            if (tw != null) tw.hide(null);
        });
        javax.swing.JOptionPane.showMessageDialog(null, "Tour finished.", "Auto Code Walker", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }
}
