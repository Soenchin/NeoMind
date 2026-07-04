package cc.neonisch.neomind.llm;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed LLM response — a reasoning string and a list of typed action objects.
 * Each action is a JsonObject with at least "type" field.
 *
 * Expected LLM output format:
 * <pre>
 * {
 *   "reasoning": "...",
 *   "actions": [ { "type": "...", ... }, ... ]
 * }
 * </pre>
 */
public record ActionPlan(String reasoning, List<JsonObject> actions) {

    private static final Logger LOG = LoggerFactory.getLogger("neomind");

    /** Try to parse LLM output; return a noop plan on any parse error. */
    public static ActionPlan fromJson(String raw) {
        List<JsonObject> actions = new ArrayList<>();
        String reasoning = "";
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            reasoning = getString(root, "reasoning");
            JsonArray arr = root.getAsJsonArray("actions");
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        actions.add(el.getAsJsonObject());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse LLM action plan: {}", e.getMessage());
        }
        if (actions.isEmpty()) {
            // Fallback: LLM returned garbage → silently noop
            JsonObject noop = new JsonObject();
            noop.addProperty("type", "noop");
            noop.addProperty("reason", "parse fallback");
            actions.add(noop);
        }
        return new ActionPlan(reasoning, actions);
    }

    /** Create a fallback ActionPlan that says the raw text as a chat message. */
    public static ActionPlan textFallback(String rawText) {
        JsonObject say = new JsonObject();
        say.addProperty("type", "say");
        say.addProperty("message", rawText);
        return new ActionPlan("raw text response", List.of(say));
    }

    public static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? "" : el.getAsString();
    }

    public static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? fallback : el.getAsInt();
    }

    public static double getDouble(JsonObject obj, String key, double fallback) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? fallback : el.getAsDouble();
    }

    public static boolean getBool(JsonObject obj, String key, boolean fallback) {
        JsonElement el = obj.get(key);
        return el == null || el.isJsonNull() ? fallback : el.getAsBoolean();
    }
}