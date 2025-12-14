package com.hackathon.ui;

import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.intellij.icons.AllIcons;
import com.hackathon.util.HtmlSanitizer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.freedesktop.dbus.spi.message.IMessageWriter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class TourToolWindow {
    private final Project project;
    private final com.intellij.openapi.wm.ToolWindow toolWindow;
    private final SimpleToolWindowPanel root;
    private final JBLabel titleLabel;
    private final JEditorPane htmlPane;
    private final JBLabel stepCounterLabel;
    private final JBPanel<?> contentPanel;
    private final JBPanel<?> emptyStatePanel;
    private final JBPanel<?> centerPanel;

    // Actions for toolbar
    private AnAction prevAction;
    private AnAction nextAction;
    private AnAction finishAction;

    public TourToolWindow(Project project, com.intellij.openapi.wm.ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;

        // Main panel with IntelliJ style - true for vertical, true for border
        this.root = new SimpleToolWindowPanel(true, true);
        root.setBackground(UIUtil.getPanelBackground());

        // Create toolbar with navigation actions
        ActionToolbar toolbar = createToolbar();
        root.setToolbar(toolbar.getComponent());

        // Create main content container
        JBPanel<?> mainContainer = new JBPanel<>(new BorderLayout());
        mainContainer.setBackground(UIUtil.getPanelBackground());

        // Header panel with title and step counter - minimal, Copilot-style
        JBPanel<?> headerPanel = new JBPanel<>(new BorderLayout());
        headerPanel.setBackground(UIUtil.getPanelBackground());
        headerPanel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(12, 16)
        ));

        titleLabel = new JBLabel("Auto Code Walker");
        titleLabel.setFont(JBUI.Fonts.label(14).asBold());
        titleLabel.setForeground(UIUtil.getLabelForeground());
        titleLabel.setIcon(AllIcons.Actions.Play_forward);

        stepCounterLabel = new JBLabel("");
        stepCounterLabel.setFont(JBUI.Fonts.smallFont());
        stepCounterLabel.setForeground(UIUtil.getContextHelpForeground());

        JBPanel<?> titleRow = new JBPanel<>(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        titleRow.add(stepCounterLabel, BorderLayout.EAST);
        headerPanel.add(titleRow, BorderLayout.CENTER);

        // Content panel for step details - card-style
        contentPanel = new JBPanel<>(new BorderLayout());
        contentPanel.setBackground(UIUtil.getPanelBackground());
        contentPanel.setBorder(JBUI.Borders.empty(16));

        // HTML pane for displaying step content - cleaner setup
        htmlPane = new JEditorPane();

        htmlPane.setContentType("text/html");          // <-- critical
        htmlPane.setEditorKit(new javax.swing.text.html.HTMLEditorKit());
        htmlPane.setEditable(false);
        htmlPane.setOpaque(false);
        htmlPane.setBorder(JBUI.Borders.empty());
        htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        htmlPane.setFont(JBUI.Fonts.label());

        JBScrollPane scrollPane = new JBScrollPane(htmlPane);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Empty state panel - modern centered design
        emptyStatePanel = createEmptyStatePanel();

        // Card layout for switching between content and empty state
        centerPanel = new JBPanel<>(new CardLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(contentPanel, "content");
        centerPanel.add(emptyStatePanel, "empty");

        // Assemble main container
        mainContainer.add(headerPanel, BorderLayout.NORTH);
        mainContainer.add(centerPanel, BorderLayout.CENTER);

        root.setContent(mainContainer);

        refresh();
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Previous step action
        prevAction = new DumbAwareAction("Previous Step", "Go to previous step", AllIcons.Actions.Back) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                TourStateService state = project.getService(TourStateService.class);
                TourStep step = state.prevStep();
                if (step != null) {
                    updateHtml(step);
                    EditorNavigationService.navigateToStep(project, step);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                TourStateService state = project.getService(TourStateService.class);
                e.getPresentation().setEnabled(state.getCurrentStepIndex() > 0);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        // Next step action
        nextAction = new DumbAwareAction("Next Step", "Go to next step", AllIcons.Actions.Forward) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                TourStateService state = project.getService(TourStateService.class);
                TourStep step = state.nextStep();
                if (step != null) {
                    updateHtml(step);
                    EditorNavigationService.navigateToStep(project, step);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                TourStateService state = project.getService(TourStateService.class);
                int idx = state.getCurrentStepIndex();
                int total = state.getSteps().size();
                e.getPresentation().setEnabled(idx < total - 1);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        // Finish tour action - use green checkmark
        finishAction = new DumbAwareAction("Finish Tour", "Complete and close the tour", AllIcons.RunConfigurations.TestPassed) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                finishTour();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                TourStateService state = project.getService(TourStateService.class);
                int idx = state.getCurrentStepIndex();
                int total = state.getSteps().size();
                e.getPresentation().setVisible(total > 0 && idx == total - 1);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        actionGroup.add(prevAction);
        actionGroup.add(nextAction);
        actionGroup.addSeparator();
        actionGroup.add(finishAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TourToolbar", actionGroup, true);
        toolbar.setTargetComponent(root);
        return toolbar;
    }

    private JBPanel<?> createEmptyStatePanel() {
        JBPanel<?> panel = new JBPanel<>(new GridBagLayout());
        panel.setOpaque(false);

        JBPanel<?> innerPanel = new JBPanel<>(new BorderLayout(0, 12));
        innerPanel.setOpaque(false);
        innerPanel.setBorder(JBUI.Borders.empty(40));

        // Icon
        JBLabel iconLabel = new JBLabel(AllIcons.General.Information);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Message
        JBLabel messageLabel = new JBLabel(
            "<html><center>" +
            "<span style='font-size:12px;font-weight:bold;'>No tour loaded</span><br/><br/>" +
            "<span style='color:gray;'>Use the editor context menu to add steps<br/>or load an existing tour file.</span>" +
            "</center></html>"
        );
        messageLabel.setFont(JBUI.Fonts.label());
        messageLabel.setForeground(UIUtil.getLabelDisabledForeground());
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        innerPanel.add(iconLabel, BorderLayout.NORTH);
        innerPanel.add(messageLabel, BorderLayout.CENTER);
        panel.add(innerPanel);

        return panel;
    }

    private void refresh() {
        TourStateService state = project.getService(TourStateService.class);
        titleLabel.setText(state.getTitle().isEmpty() ? "Auto Code Walker" : state.getTitle());
        TourStep current = state.getCurrentStep();

        if (centerPanel.getLayout() instanceof CardLayout cardLayout) {
            if (current != null) {
                updateHtml(current);
                cardLayout.show(centerPanel, "content");
            } else {
                cardLayout.show(centerPanel, "empty");
                stepCounterLabel.setText("");
                if (toolWindow != null) {
                    toolWindow.setStripeTitle("Auto Code Walker");
                }
            }
        }
    }

    private void updateHtml(TourStep step) {
        TourStateService state = project.getService(TourStateService.class);
        titleLabel.setText(state.getTitle().isEmpty() ? "Auto Code Walker" : state.getTitle());

        // Build styled HTML content: Only Author Note + AI explanation (sanitized). Never display code.
        StringBuilder contentBuilder = new StringBuilder();
        if (step.authorNote() != null && !step.authorNote().isBlank()) {
            contentBuilder.append("<table width='100%' cellpadding='8' cellspacing='0' style='margin-bottom: 12px;'>")
                    // Dark gray box with white text before the Summary
                    .append("<tr><td style='background-color: #1f1f1f; color: #ffffff; border-left: 3px solid #3b82f6;'>")
                    .append("<b>").append(escape(step.authorNote())).append("</b></td></tr></table>");
        }
        String ai = HtmlSanitizer.stripCodeBlocks(step.aiExplanation());
        // Ensure a visual break before any "Example usage" or "Example call" section
        if (ai != null) {
            ai = ai.replaceAll("(?i)Example\\s+usage\\s*:", "<br/><br/>Example usage:");
            ai = ai.replaceAll("(?i)Example\\s+call\\s*:", "<br/><br/>Example call:");
        }
        if (ai != null && !ai.isBlank()) {
            contentBuilder.append(ai);
        }
        String content = contentBuilder.toString();

        // Get theme-aware colors
        Color fgColor = UIUtil.getLabelForeground();
        Color bgColor = UIUtil.getPanelBackground();
        Color linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED;
        Color secondaryColor = UIUtil.getContextHelpForeground();


        String fgHex = colorToHex(fgColor);
        String bgHex = colorToHex(bgColor);
        String linkHex = colorToHex(linkColor);
        String secondaryHex = colorToHex(secondaryColor);

        boolean isDark = !JBColor.isBright();
        String codeBlockBg = isDark ? "#2d2d2d" : "#f6f8fa";

        // Use only CSS properties supported by Java's Swing HTML parser
        String styledHtml = "<html><head><style>" +
                "body { font-family: sans-serif; font-size: 13pt; color: " + fgHex + "; " +
                "background-color: " + bgHex + "; margin: 0; padding: 0; }" +
                "h3 { font-size: 15pt; margin-top: 0; margin-bottom: 12px; color: " + fgHex + "; }" +
                "h4 { font-size: 13pt; margin-top: 16px; margin-bottom: 8px; color: " + fgHex + "; }" +
                "p { margin-top: 8px; margin-bottom: 8px; }" +
                // Keep styles minimal; we don't show code blocks but preserve some defaults
                "pre { display:none; } code { display:none; }" +
                "a { color: " + linkHex + "; }" +
                "ul { margin-top: 8px; margin-bottom: 8px; margin-left: 24px; }" +
                "ol { margin-top: 8px; margin-bottom: 8px; margin-left: 24px; }" +
                "li { margin-top: 4px; margin-bottom: 4px; }" +
                "</style></head><body>" + content;

        // Add file location metadata
        if (step.filePath() != null) {
            String fileName = step.filePath().substring(Math.max(0, step.filePath().lastIndexOf('/') + 1));
            if (fileName.isEmpty()) {
                fileName = step.filePath().substring(Math.max(0, step.filePath().lastIndexOf('\\') + 1));
            }
            String lineInfo = step.endLine() != null && step.endLine() != step.lineNum()
                ? "Lines " + step.lineNum() + "-" + step.endLine()
                : "Line " + step.lineNum();
            styledHtml += "<hr><p style='font-size: 11pt; color: " + secondaryHex + ";'>" +
                    escape(fileName) + " &bull; " + lineInfo + "</p>";
        }

        styledHtml += "</body></html>";


        htmlPane.setText(styledHtml);
        htmlPane.setCaretPosition(0);

        // Update step counter
        int idx = state.getCurrentStepIndex();
        int total = state.getSteps().size();

        String stepText = (idx + 1) + " / " + total;
        stepCounterLabel.setText(stepText);

        if (toolWindow != null) {
            toolWindow.setStripeTitle("Auto Code Walker (" + (idx + 1) + "/" + total + ")");
        }
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public JComponent getComponent() {
        return root;
    }

    private void finishTour() {
        TourStateService state = project.getService(TourStateService.class);
        state.clear();
        EditorNavigationService.clearHighlight();

        SwingUtilities.invokeLater(() -> {
            com.intellij.openapi.wm.ToolWindow tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow("Auto Code Walker");
            if (tw != null) tw.hide(null);
        });

        // Use IntelliJ's balloon notification for a modern look
        com.intellij.notification.Notification notification = new com.intellij.notification.Notification(
                "Auto Code Walker",
                "Tour completed!",
                "You have finished the code tour.",
                com.intellij.notification.NotificationType.INFORMATION
        );
        com.intellij.notification.Notifications.Bus.notify(notification, project);

        refresh();
    }
}

