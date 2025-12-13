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

@Service
public final class OpenAIService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final Gson gson = new Gson();

    public record ExplanationResult(String title, String htmlContent) {}

    /**
     * Generate HTML explanation for a code snippet with an author note.
     * Requirements: explanation must be precise and include at least one concrete usage example.
     * Uses response_format json_object to get back {"title": ..., "html_content": ...}.
     */
    public @NotNull ExplanationResult generateExplanation(@NotNull String code, @NotNull String note) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // No API key, return null for AI explanation
            return new ExplanationResult("Auto Code Walker Tour", null);
        }

        try {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys: " +
                    "'title' (short summary), " +
                    "'explanation' (clear explanation of the code), " +
                    "'usage_example' (a short code snippet showing how to call/use this code). " +
                    "Do not include markdown formatting in the JSON values.");

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", "Author note: " + note + "\n\nCode:\n" + code);

            JsonArray messages = new JsonArray();
            messages.add(system);
            messages.add(user);

            JsonObject body = new JsonObject();
            body.addProperty("model", "gpt-4o-mini");
            body.add("messages", messages);
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    if (msg != null) {
                        String content = msg.get("content").getAsString();
                        JsonObject parsed = gson.fromJson(content, JsonObject.class);
                        String title = parsed.has("title") ? parsed.get("title").getAsString() : "Auto Code Walker Tour";

                        String expl = parsed.has("explanation") ? parsed.get("explanation").getAsString() : "";
                        String usage = parsed.has("usage_example") ? parsed.get("usage_example").getAsString() : "";

                        String html = "<h3>Explanation</h3><p>" + escape(expl) + "</p>" +
                                      "<h3>Usage Example</h3><pre>" + escape(usage) + "</pre>";

                        return new ExplanationResult(title, html);
                    }
                }
            }
        } catch (Exception e) {
            // ignore and fall back
        }

        // AI request failed, return null for AI explanation
        return new ExplanationResult("Auto Code Walker Tour", null);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static volatile String CACHED_API_KEY;

    private static String getApiKey() {
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
        // 3) .env in the first open project base path
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
}
