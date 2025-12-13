package com.hackathon.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class StepCreationDialog extends DialogWrapper {
    private final JTextArea noteArea = new JTextArea(5, 40);

    public StepCreationDialog(@Nullable Project project) {
        super(project);
        setTitle("Add Tour Step");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("Author Note:"), BorderLayout.NORTH);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(noteArea), BorderLayout.CENTER);
        return panel;
    }

    public String getNote() {
        return noteArea.getText().trim();
    }
}
