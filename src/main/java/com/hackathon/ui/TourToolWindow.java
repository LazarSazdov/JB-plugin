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
        controls.add(prevBtn);
        controls.add(nextBtn);
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
        }
    }

    private void updateHtml(TourStep step) {
        titleLabel.setText("Auto Code Walker: " + project.getService(TourStateService.class).getTitle());
        String html = step.aiExplanation() != null && !step.aiExplanation().isBlank()
                ? step.aiExplanation()
                : ("<h3>" + step.authorNote() + "</h3><pre>" + escape(step.codeSnippet()) + "</pre>");
        htmlPane.setText("<html><body>" + html + "</body></html>");
        htmlPane.setCaretPosition(0);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public JComponent getComponent() {
        return root;
    }
}
