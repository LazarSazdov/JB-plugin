package com.hackathon.actions;

import com.google.gson.Gson;
import com.hackathon.model.Tour;
import com.hackathon.model.TourStep;
import com.hackathon.openai.OpenAIService;
import com.hackathon.util.HtmlSanitizer;
import com.hackathon.service.SelectionModeService;
import com.hackathon.service.TourStateService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a tour.json in the project root with AI explanations for selected symbols.
 */
public class FinalizeTourAction extends AnAction {
    private final Gson gson = new Gson();

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean visible = false;
        boolean enabled = true;
        if (project != null) {
            // Visible while selection mode is ON so the user always sees a Finish entry.
            com.hackathon.service.SelectionModeService sel = project.getService(com.hackathon.service.SelectionModeService.class);
            visible = sel != null && sel.isEnabled();
            // Enabled if there is at least one selection; otherwise we still show it but it will inform the user.
            TourStateService state = project.getService(TourStateService.class);
            enabled = state != null && !state.getSteps().isEmpty();
        }
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        TourStateService state = project.getService(TourStateService.class);
        if (state.getSteps().isEmpty()) {
            Messages.showInfoMessage(project, "No selections. Use 'Create Tour (Select Functions)' first.", "Create Tour");
            return;
        }

        // Run concurrent AI generation with progress UI
        OpenAIService ai = com.intellij.openapi.application.ApplicationManager.getApplication().getService(OpenAIService.class);
        List<TourStep> steps = new ArrayList<>(state.getSteps());

        // Dedupe identical code+note pairs to avoid repeated calls
        Map<String, CompletableFuture<OpenAIService.ExplanationResult>> futureByKey = new HashMap<>();
        Map<String, List<Integer>> indicesByKey = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            TourStep s = steps.get(i);
            String note = s.authorNote() == null ? "" : s.authorNote();
            String key = makeKey(s.codeSnippet(), note);
            indicesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            // futures created later with throttling
        }

        int unique = indicesByKey.size();
        AtomicInteger completed = new AtomicInteger();

        List<String> keys = new ArrayList<>(indicesByKey.keySet());

        AtomicBoolean canceled = new AtomicBoolean(false);
        AtomicInteger failures = new AtomicInteger(0);
        // Thread-safe queue to collect results as they arrive
        record IndexedResult(int index, OpenAIService.ExplanationResult result) {}
        ConcurrentLinkedQueue<IndexedResult> responseQueue = new ConcurrentLinkedQueue<>();

        ProgressManager.getInstance().run(new Task.Modal(project, "Generating AI summaries (" + unique + ")", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(unique <= 1);

                int cfgConc = getConcurrency();
                final int concurrency = cfgConc <= 0 ? Math.max(1, keys.size()) : Math.max(1, cfgConc);
                final AtomicInteger nextIndex = new AtomicInteger(0);

                // Helper to start the next request respecting concurrency
                final Runnable startNext = new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) return;
                        int idx = nextIndex.getAndIncrement();
                        if (idx >= keys.size()) return;

                        String key = keys.get(idx);
                        List<Integer> positions = indicesByKey.get(key);
                        if (positions == null || positions.isEmpty()) {
                            // nothing to process for this key, try next
                            run();
                            return;
                        }
                        TourStep s = steps.get(positions.get(0));
                        String note = s.authorNote() == null ? "" : s.authorNote();

                        // Use project-aware async API so .env from this project is respected
                        CompletableFuture<OpenAIService.ExplanationResult> fut = ai.generateExplanationAsync(project, s.codeSnippet(), note);
                        futureByKey.put(key, fut);

                        fut.whenComplete((res, err) -> {
                            if (err != null || res == null || res.htmlContent() == null) {
                                failures.incrementAndGet();
                            } else {
                                // Enqueue result for every step that shares this key
                                for (Integer pos : positions) {
                                    responseQueue.add(new IndexedResult(pos, res));
                                }
                            }
                            int done = completed.incrementAndGet();
                            if (indicator.isCanceled()) {
                                canceled.set(true);
                            }
                            // Update progress bar text and fraction
                            indicator.setText("Generating AI summaries: " + done + "/" + unique);
                            if (failures.get() > 0) {
                                indicator.setText2(failures.get() + " failed");
                            } else {
                                indicator.setText2("");
                            }
                            indicator.setFraction(unique == 0 ? 1.0 : Math.min(1.0, (double) done / unique));

                            // Schedule next if any
                            if (!canceled.get()) {
                                this.run();
                            }
                        });
                    }
                };

                // Prime the pipeline with up to N concurrent requests
                int initial = Math.min(concurrency, keys.size());
                for (int i = 0; i < initial; i++) {
                    if (indicator.isCanceled()) { canceled.set(true); break; }
                    startNext.run();
                }

                // Wait until all are done or canceled
                while (!canceled.get() && completed.get() < unique) {
                    if (indicator.isCanceled()) {
                        canceled.set(true);
                        break;
                    }
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                }

                // If canceled, try to cancel in-flight futures
                if (canceled.get()) {
                    futureByKey.values().forEach(f -> f.cancel(true));
                } else {
                    // Ensure all futures have finished
                    try {
                        CompletableFuture.allOf(futureByKey.values().toArray(new CompletableFuture[0])).join();
                    } catch (Throwable ignored) {}
                }
            }
        });

        // If user canceled, stop here without saving/updating state
        if (canceled.get()) {
            return;
        }

        // Build updated steps using results queue; sanitize HTML; pick title from first result when needed
        List<TourStep> updated = new ArrayList<>(steps.size());
        String title = state.getTitle();
        OpenAIService.ExplanationResult[] resultsByIndex = new OpenAIService.ExplanationResult[steps.size()];
        // Drain the queue atomically now that all futures are finished
        IndexedResult ir;
        while ((ir = responseQueue.poll()) != null) {
            if (ir.index >= 0 && ir.index < resultsByIndex.length) {
                resultsByIndex[ir.index] = ir.result;
            }
        }
        for (int i = 0; i < steps.size(); i++) {
            TourStep s = steps.get(i);
            OpenAIService.ExplanationResult result = resultsByIndex[i];
            String html = result != null ? HtmlSanitizer.stripCodeBlocks(result.htmlContent()) : null;
            if ((title == null || title.isBlank() || "Untitled Tour".equals(title)) && result != null && result.title() != null && !result.title().isBlank()) {
                title = result.title();
            }
            updated.add(new TourStep(s.filePath(), s.lineNum(), s.codeSnippet(), s.authorNote(), html, s.endLine(), s.symbolName(), s.type()));
        }

        // If nothing succeeded, surface a helpful hint
        if (failures.get() >= unique) {
            Messages.showWarningDialog(project,
                    "No AI summaries were generated. Please check your OPENAI_API_KEY (in environment, JVM system property, or .env) and network connectivity.",
                    "Auto Code Walker");
        }

        String suggested = title == null || title.isBlank() ? "Auto Code Walker Tour" : title;
        String input = Messages.showInputDialog(project, "Tour title:", "Create Tour", null, suggested, null);
        if (input != null && !input.isBlank()) title = input.trim();
        state.setTour(new Tour(title, updated));

        String basePath = project.getBasePath();
        if (basePath == null) basePath = new File(".").getAbsolutePath();
        File out = new File(basePath, "tour.json");
        if (out.exists()) {
            int choice = Messages.showYesNoDialog(project,
                    "File tour.json already exists in project root. Overwrite?",
                    "Create Tour",
                    "Overwrite",
                    "Cancel",
                    null);
            if (choice != Messages.YES) return;
        }
        try (FileWriter fw = new FileWriter(out, StandardCharsets.UTF_8)) {
            gson.toJson(new Tour(title, updated), fw);
            Messages.showInfoMessage(project, "Tour saved to: " + out.getAbsolutePath(), "Create Tour");
        } catch (Exception ex) {
            Messages.showErrorDialog(project, "Failed to save tour: " + ex.getMessage(), "Create Tour");
        }

        // Disable selection mode and clear state to start fresh
        project.getService(SelectionModeService.class).setEnabled(false);
        state.clear();
    }

    private static String makeKey(String code, String note) {
        String a = Objects.toString(code, "");
        String b = Objects.toString(note, "");
        return a.length() + ":" + a.hashCode() + ":" + b.hashCode();
    }

    private static int getConcurrency() {
        // Fixed bounded concurrency: process requests 4-at-a-time regardless of env/system properties.
        return 4;
    }
}
