package com.hackathon.ui;

import com.hackathon.model.TourStep;
import com.hackathon.service.EditorNavigationService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class TourToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        TourToolWindow panel = new TourToolWindow(project, toolWindow);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel.getComponent(), "Tour", false);
        toolWindow.getContentManager().addContent(content);

        // Listen for tool window visibility changes
        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            private boolean wasVisible = false;

            @Override
            public void stateChanged(@NotNull com.intellij.openapi.wm.ToolWindowManager toolWindowManager) {
                ToolWindow tw = toolWindowManager.getToolWindow("Auto Code Walker");
                if (tw != null) {
                    boolean isVisible = tw.isVisible();
                    if (isVisible != wasVisible) {
                        wasVisible = isVisible;
                        if (isVisible) {
                            // Restore highlight if we have a current step
                            TourStateService state = project.getService(TourStateService.class);
                            TourStep current = state.getCurrentStep();
                            if (current != null) {
                                EditorNavigationService.navigateToStep(project, current);
                            }
                        }
                    }
                }
            }
        });
    }
}
