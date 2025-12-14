package com.hackathon.openai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.security.MessageDigest;

@Service
public final class OpenAIService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Gson gson = new Gson();

    // Simple in-memory LRU cache (session-scoped) to avoid duplicate requests
    private final Map<String, ExplanationResult> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ExplanationResult> eldest) {
                    return size() > 256; // cap
                }
            }
    );

    public record ExplanationResult(String title, String htmlContent) {}

    /**
     * Generate HTML summary for a code snippet with an author note.
     * Requirements: explanation must be precise. We intentionally avoid including code blocks in the HTML output.
     * Uses response_format json_object to get back {"title": ..., ...}.
     */
    public @NotNull ExplanationResult generateExplanation(@NotNull String code, @NotNull String note) {
        String apiKey = getApiKey(null);
        if (apiKey == null || apiKey.isBlank()) {
            // No API key, return null for AI explanation
            return new ExplanationResult("Auto Code Walker Tour", null);
        }

        try {
            String model = getModel(null);
            String codeToSend = maybeTruncate(code);
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys: " +
                    "'title' (short summary), " +
                    "'explanation' (a concise summary; DO NOT include any code snippets. Include a usage example at the end). " +
                    "Do not include markdown formatting in the JSON values." +
                    " Example: { \"title\": \"Method Summary\", \"explanation\": \"This method calculates the sum of two integers. Example call: sum(1, 2).\" }");

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", "Author note: " + note + "\n\nCode:\n" + codeToSend);

            JsonArray messages = new JsonArray();
            messages.add(system);
            messages.add(user);

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages);
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);
            // Keep outputs small and deterministic
            body.addProperty("temperature", 0.2);
            body.addProperty("max_tokens", 350);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    // Rely on default transparent decompression; do not force gzip for maximal compatibility
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return parseCompletionBody(resp.body());
            } else {
                logHttpFailure("sync", resp);
            }
        } catch (Exception e) {
            // Log and fall back
            com.intellij.openapi.diagnostic.Logger.getInstance(OpenAIService.class)
                    .warn("OpenAI sync request failed: " + e.getMessage());
        }

        // AI request failed, return null for AI explanation
        return new ExplanationResult("Auto Code Walker Tour", null);
    }

    /** Async variant with small retry and LRU caching. */
    public @NotNull CompletableFuture<ExplanationResult> generateExplanationAsync(@NotNull String code, @NotNull String note) {
        String apiKey = getApiKey(null);
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(new ExplanationResult("Auto Code Walker Tour", null));
        }

        String model = getModel(null);
        String codeToSend = maybeTruncate(code);
        String cacheKey = cacheKey(model, codeToSend, note);
        ExplanationResult cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys: " +
                "'title' (short summary), " +
                "'explanation' (a concise summary; DO NOT include any code snippets. Include a usage example at the end). " +
                "Do not include markdown formatting in the JSON values." +
                " Example: { \"title\": \"Method Summary\", \"explanation\": \"This method calculates the sum of two integers. Example call: sum(1, 2).\" }");

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "Author note: " + note + "\n\nCode:\n" + codeToSend);

        JsonArray messages = new JsonArray();
        messages.add(system);
        messages.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 350);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                // Avoid forcing gzip to reduce chances of decompression issues
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                .build();

        return sendWithRetry(request, 3)
                .thenApply(resp -> {
                    try {
                        if (resp != null && resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            ExplanationResult r = parseCompletionBody(resp.body());
                            if (r != null) {
                                cache.put(cacheKey, r);
                                return r;
                            }
                        } else if (resp != null) {
                            logHttpFailure("async-noproject", resp);
                        }
                    } catch (Throwable ignore) {}
                    return new ExplanationResult("Auto Code Walker Tour", null);
                })
                .exceptionally(ex -> new ExplanationResult("Auto Code Walker Tour", null));
    }

    /** Project-aware sync variant. Reads .env from the specific project's base path. */
    public @NotNull ExplanationResult generateExplanation(@org.jetbrains.annotations.Nullable com.intellij.openapi.project.Project project,
                                                          @NotNull String code,
                                                          @NotNull String note) {
        String apiKey = getApiKey(project);
        if (apiKey == null || apiKey.isBlank()) {
            return new ExplanationResult("Auto Code Walker Tour", null);
        }
        try {
            String model = getModel(project);
            String codeToSend = maybeTruncate(code);
            com.google.gson.JsonObject system = new com.google.gson.JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys: " +
                    "'title' (short summary), " +
                    "'explanation' (a concise summary; DO NOT include any code snippets. Include a usage example at the end). " +
                    "Do not include markdown formatting in the JSON values." +
                    " Example: { \"title\": \"Method Summary\", \"explanation\": \"This method calculates the sum of two integers. Example call: sum(1, 2).\" }");

            com.google.gson.JsonObject user = new com.google.gson.JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", "Author note: " + note + "\n\nCode:\n" + codeToSend);

            com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
            messages.add(system);
            messages.add(user);

            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.addProperty("model", model);
            body.add("messages", messages);
            com.google.gson.JsonObject responseFormat = new com.google.gson.JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);
            body.addProperty("temperature", 0.2);
            body.addProperty("max_tokens", 350);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(gson.toJson(body), java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return parseCompletionBody(resp.body());
            } else {
                logHttpFailure("sync-project", resp);
            }
        } catch (Exception ignore) {}
        return new ExplanationResult("Auto Code Walker Tour", null);
    }

    /** Project-aware async variant. */
    public @NotNull CompletableFuture<ExplanationResult> generateExplanationAsync(@org.jetbrains.annotations.Nullable com.intellij.openapi.project.Project project,
                                                                                  @NotNull String code,
                                                                                  @NotNull String note) {
        String apiKey = getApiKey(project);
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(new ExplanationResult("Auto Code Walker Tour", null));
        }

        String model = getModel(project);
        String codeToSend = maybeTruncate(code);
        String cacheKey = cacheKey(model, codeToSend, note);
        ExplanationResult cached = cache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        com.google.gson.JsonObject system = new com.google.gson.JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys: " +
                "'title' (short summary), " +
                "'explanation' (a concise summary; DO NOT include any code snippets. Include a usage example at the end). " +
                "Do not include markdown formatting in the JSON values." +
                " Example: { \"title\": \"Method Summary\", \"explanation\": \"This method calculates the sum of two integers. Example call: sum(1, 2).\" }");

        com.google.gson.JsonObject user = new com.google.gson.JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "Author note: " + note + "\n\nCode:\n" + codeToSend);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        messages.add(system);
        messages.add(user);

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        com.google.gson.JsonObject responseFormat = new com.google.gson.JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 350);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                // Avoid forcing gzip to reduce chances of decompression issues
                .header("Authorization", "Bearer " + apiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(gson.toJson(body), java.nio.charset.StandardCharsets.UTF_8))
                .build();

        return sendWithRetry(request, 3)
                .thenApply(resp -> {
                    try {
                        if (resp != null && resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            ExplanationResult r = parseCompletionBody(resp.body());
                            if (r != null) {
                                cache.put(cacheKey, r);
                                return r;
                            }
                        } else if (resp != null) {
                            logHttpFailure("async-project", resp);
                        }
                    } catch (Throwable ignore) {}
                    return new ExplanationResult("Auto Code Walker Tour", null);
                })
                .exceptionally(ex -> new ExplanationResult("Auto Code Walker Tour", null));
    }

    // -- Helpers ------------------------------------------------------------

    /**
     * Parse a Chat Completions response body into an ExplanationResult.
     * Falls back to treating the message content as plain text if JSON parsing fails.
     */
    private ExplanationResult parseCompletionBody(String body) {
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return new ExplanationResult("Auto Code Walker Tour", null);
            JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (msg == null || !msg.has("content")) return new ExplanationResult("Auto Code Walker Tour", null);
            String content = msg.get("content").isJsonNull() ? "" : msg.get("content").getAsString();

            // Try strict JSON first
            try {
                JsonObject parsed = gson.fromJson(content, JsonObject.class);
                String title = parsed.has("title") ? parsed.get("title").getAsString() : "Auto Code Walker Tour";
                String expl = parsed.has("explanation") ? parsed.get("explanation").getAsString() : content;
                String html = "<h3>Summary</h3><p>" + escape(expl) + "</p>";
                return new ExplanationResult(title, html);
            } catch (Throwable ignore) {
                // Fallback: use content as-is (plain text)
                String title = "Auto Code Walker Tour";
                String html = content == null || content.isBlank() ? null : ("<h3>Summary</h3><p>" + escape(content) + "</p>");
                return new ExplanationResult(title, html);
            }
        } catch (Throwable e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(OpenAIService.class)
                    .warn("OpenAI parse error: " + e.getMessage());
            return new ExplanationResult("Auto Code Walker Tour", null);
        }
    }

    private void logHttpFailure(String tag, HttpResponse<String> resp) {
        try {
            String snippet = resp.body();
            if (snippet != null && snippet.length() > 500) snippet = snippet.substring(0, 500) + "...";
            com.intellij.openapi.diagnostic.Logger.getInstance(OpenAIService.class)
                    .warn("OpenAI " + tag + " HTTP " + resp.statusCode() + ": " + snippet);
        } catch (Throwable ignore) {}
    }

    private CompletableFuture<HttpResponse<String>> sendWithRetry(HttpRequest request, int maxAttempts) {
        CompletableFuture<HttpResponse<String>> fut = new CompletableFuture<>();
        attemptSend(request, 1, maxAttempts, fut);
        return fut;
    }

    private void attemptSend(HttpRequest request, int attempt, int maxAttempts, CompletableFuture<HttpResponse<String>> sink) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((resp, err) -> {
                    boolean retry;
                    if (err != null) {
                        retry = attempt < maxAttempts;
                    } else {
                        int sc = resp.statusCode();
                        retry = (sc == 429 || sc >= 500) && attempt < maxAttempts;
                    }
                    if (!retry) {
                        if (err != null) sink.completeExceptionally(err); else sink.complete(resp);
                        return;
                    }
                    long delayMs = (long) Math.min(2000, 300 * Math.pow(2, attempt - 1));
                    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                            .execute(() -> attemptSend(request, attempt + 1, maxAttempts, sink));
                });
    }

    private static String cacheKey(String model, String code, String note) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Objects.toString(model, "").getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(code.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(Objects.toString(note, "").getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            return model + ":" + code.hashCode() + ":" + note.hashCode();
        }
    }

    private static String maybeTruncate(String code) {
        int max = getMaxCodeChars();
        if (max > 0 && code != null && code.length() > max) {
            // Keep head and tail when very long
            int head = (int) (max * 0.7);
            int tail = max - head;
            return code.substring(0, head) + "\n...\n" + code.substring(code.length() - tail);
        }
        return code;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static volatile String CACHED_API_KEY;
    private static volatile String CACHED_MODEL;

    private static String getApiKey() { // backwards-compatible
        return getApiKey(null);
    }

    private static String getApiKey(@org.jetbrains.annotations.Nullable com.intellij.openapi.project.Project project) {
        // Cached once per app lifecycle
        String cached = CACHED_API_KEY;
        if (cached != null) return cached;

        // 1) Environment variable
        String key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) {
            CACHED_API_KEY = key.trim();
            return CACHED_API_KEY;
        }
        // 2) System property
        key = System.getProperty("OPENAI_API_KEY");
        if (key != null && !key.isBlank()) {
            CACHED_API_KEY = key.trim();
            return CACHED_API_KEY;
        }
        // 3) .env in the provided project's base path
        if (project != null) {
            try {
                String base = project.getBasePath();
                if (base != null) {
                    java.io.File env = new java.io.File(base, ".env");
                    if (env.exists()) {
                        java.nio.file.Path p = env.toPath();
                        for (String line : java.nio.file.Files.readAllLines(p)) {
                            String ln = line.trim();
                            if (ln.startsWith("#") || ln.isEmpty()) continue;
                            int eq = ln.indexOf('=');
                            if (eq > 0) {
                                String k = ln.substring(0, eq).trim();
                                String v = ln.substring(eq + 1).trim();
                                if ((k.equals("OPENAI_API_KEY") || k.equals("openai_api_key")) && !v.isEmpty()) {
                                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                        v = v.substring(1, v.length() - 1);
                                    }
                                    CACHED_API_KEY = v;
                                    return CACHED_API_KEY;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        // 4) .env in the first open project base path
        try {
            com.intellij.openapi.project.Project[] projects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (projects.length > 0) {
                String base = projects[0].getBasePath();
                if (base != null) {
                    java.io.File env = new java.io.File(base, ".env");
                    if (env.exists()) {
                        java.nio.file.Path p = env.toPath();
                        for (String line : java.nio.file.Files.readAllLines(p)) {
                            String ln = line.trim();
                            if (ln.startsWith("#") || ln.isEmpty()) continue;
                            int eq = ln.indexOf('=');
                            if (eq > 0) {
                                String k = ln.substring(0, eq).trim();
                                String v = ln.substring(eq + 1).trim();
                                if ((k.equals("OPENAI_API_KEY") || k.equals("openai_api_key")) && !v.isEmpty()) {
                                    // Remove optional surrounding quotes
                                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                        v = v.substring(1, v.length() - 1);
                                    }
                                    CACHED_API_KEY = v;
                                    return CACHED_API_KEY;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** Resolve model from env/system/.env (OPENAI_MODEL). Defaults to gpt-4o-mini. */
    public static String getModel() { // backwards-compatible
        return getModel(null);
    }

    public static String getModel(@org.jetbrains.annotations.Nullable com.intellij.openapi.project.Project project) {
        String m = CACHED_MODEL;
        if (m != null) return m;
        String key = System.getenv("OPENAI_MODEL");
        if (key != null && !key.isBlank()) return CACHED_MODEL = key.trim();
        key = System.getProperty("OPENAI_MODEL");
        if (key != null && !key.isBlank()) return CACHED_MODEL = key.trim();
        // Project-specific .env
        if (project != null) {
            try {
                String base = project.getBasePath();
                if (base != null) {
                    java.io.File env = new java.io.File(base, ".env");
                    if (env.exists()) {
                        java.nio.file.Path p = env.toPath();
                        for (String line : java.nio.file.Files.readAllLines(p)) {
                            String ln = line.trim();
                            if (ln.startsWith("#") || ln.isEmpty()) continue;
                            int eq = ln.indexOf('=');
                            if (eq > 0) {
                                String k = ln.substring(0, eq).trim();
                                String v = ln.substring(eq + 1).trim();
                                if ((k.equals("OPENAI_MODEL") || k.equalsIgnoreCase("openai_model")) && !v.isEmpty()) {
                                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                        v = v.substring(1, v.length() - 1);
                                    }
                                    return CACHED_MODEL = v;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
        }
        // Fallback to first open project
        try {
            com.intellij.openapi.project.Project[] projects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
            if (projects.length > 0) {
                String base = projects[0].getBasePath();
                if (base != null) {
                    java.io.File env = new java.io.File(base, ".env");
                    if (env.exists()) {
                        java.nio.file.Path p = env.toPath();
                        for (String line : java.nio.file.Files.readAllLines(p)) {
                            String ln = line.trim();
                            if (ln.startsWith("#") || ln.isEmpty()) continue;
                            int eq = ln.indexOf('=');
                            if (eq > 0) {
                                String k = ln.substring(0, eq).trim();
                                String v = ln.substring(eq + 1).trim();
                                if ((k.equals("OPENAI_MODEL") || k.equalsIgnoreCase("openai_model")) && !v.isEmpty()) {
                                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                        v = v.substring(1, v.length() - 1);
                                    }
                                    return CACHED_MODEL = v;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return CACHED_MODEL = "gpt-4o-mini";
    }

    private static int getMaxCodeChars() {
        String v = System.getenv("ACW_MAX_CODE_CHARS");
        if (v == null || v.isBlank()) v = System.getProperty("ACW_MAX_CODE_CHARS");
        if (v == null || v.isBlank()) return 8000; // Safe default to avoid oversized payloads
        try { return Integer.parseInt(v.trim()); } catch (Exception ignore) { return 8000; }
    }
}
