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

    public static record ExplanationResult(String title, String htmlContent) {}

    /**
     * Generate HTML explanation for a code snippet with an author note.
     * Uses response_format json_object to get back {"title": ..., "html_content": ...}.
     */
    public @Nullable ExplanationResult generateExplanation(@NotNull String code, @NotNull String note) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            // Fallback local explanation
            String title = "Auto Code Walker Tour";
            String html = "<h3>Author Note</h3><p>" + escape(note) + "</p>" +
                    "<h3>Code</h3><pre>" + escape(code) + "</pre>" +
                    "<p><em>(Set OPENAI_API_KEY to enable AI explanations.)</em></p>";
            return new ExplanationResult(title, html);
        }

        try {
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", "You are an expert code tour guide. Return a JSON object with keys 'title' (string for the tour/group title) and 'html_content' (HTML fragment suitable for a JEditorPane). Keep it concise and focused on the selected code. Do NOT include markdown fences.");

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
                if (choices != null && choices.size() > 0) {
                    JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    if (msg != null) {
                        String content = msg.get("content").getAsString();
                        JsonObject parsed = gson.fromJson(content, JsonObject.class);
                        String title = parsed.has("title") ? parsed.get("title").getAsString() : "Auto Code Walker Tour";
                        String html = parsed.has("html_content") ? parsed.get("html_content").getAsString() : "<p>No content</p>";
                        return new ExplanationResult(title, html);
                    }
                }
            }
        } catch (Exception e) {
            // ignore and fall back
        }

        String title = "Auto Code Walker Tour";
        String html = "<h3>Author Note</h3><p>" + escape(note) + "</p>" +
                "<h3>Code</h3><pre>" + escape(code) + "</pre>" +
                "<p><em>(AI request failed.)</em></p>";
        return new ExplanationResult(title, html);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
