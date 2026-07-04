package cc.neonisch.neomind.llm;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible API client for LLM calls.
 * Runs synchronously on a dedicated thread — all requests are fire-and-forget
 * from the caller's perspective, with a timeout guard.
 */
public final class LLMClient {

    private static final Logger LOG = LoggerFactory.getLogger("neomind");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson GSON = new Gson();

    /** Parse LLM response into an ActionPlan, or return null on failure. */
    public static ActionPlan ask(
            String endpoint,
            String model,
            String apiKey,
            int timeoutMs,
            int maxTokens,
            double temperature,
            String systemPrompt,
            String userMessage
    ) {
        try {
            // ── Build request body ──
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("temperature", temperature);

            JsonArray messages = new JsonArray();
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", systemPrompt);
            messages.add(sys);

            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", userMessage);
            messages.add(user);

            body.add("messages", messages);

            // ── Send request ──
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("LLM API returned {}: {}", response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }

            // ── Parse response ──
            JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                LOG.warn("LLM response has no choices");
                return null;
            }

            String content = choices.get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();

            if (content == null || content.isBlank()) {
                LOG.warn("LLM returned empty content");
                return ActionPlan.textFallback("（模型返回了空白回复，可能是被限流或 prompt 冲突，稍后重试）");
            }

            LOG.info("LLM raw content (first 200 chars): {}", content.substring(0, Math.min(200, content.length())));

            // Strip markdown fences if present
            content = content.trim();
            if (content.startsWith("```")) {
                int nl = content.indexOf('\n', 3);
                if (nl > 0) content = content.substring(nl + 1).trim();
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3).trim();
                }
            }

            // If LLM returned non-JSON (plain text), fallback: wrap as say action
            if (!content.startsWith("{")) {
                LOG.warn("LLM returned non-JSON: {}", content.substring(0, Math.min(100, content.length())));
                return ActionPlan.textFallback(content);
            }

            return ActionPlan.fromJson(content);

        } catch (Exception e) {
            LOG.error("LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}