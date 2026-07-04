package cc.neonisch.neomind.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for calling NeoSightAPI region endpoints.
 * All calls are blocking with a 3-second timeout. Silent on failure.
 */
public final class SightApiClient {

    private static final Logger LOG = LoggerFactory.getLogger("neomind-sightapi");
    private static final String BASE = "http://127.0.0.1:8345";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    // ─── Region CRUD ───────────────────────────────────────

    /**
     * Create a region via POST /api/regions.
     * @return created region JsonObject, or null on failure
     */
    public static JsonObject createRegion(String name, String owner, String dimension,
                                           int x1, int z1, int x2, int z2, String label) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("owner", owner);
        body.addProperty("dimension", dimension);
        body.addProperty("x1", x1);
        body.addProperty("z1", z1);
        body.addProperty("x2", x2);
        body.addProperty("z2", z2);
        if (label != null) body.addProperty("label", label);

        String resp = post("/api/regions", body.toString());
        if (resp == null) return null;
        try { return JsonParser.parseString(resp).getAsJsonObject(); }
        catch (Exception e) { return null; }
    }

    /**
     * List all regions via GET /api/regions.
     * @return list of region JsonObjects, empty list on failure
     */
    public static List<JsonObject> listRegions() {
        String resp = get("/api/regions");
        if (resp == null) return Collections.emptyList();
        try {
            JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();
            List<JsonObject> list = new java.util.ArrayList<>();
            for (JsonElement el : arr) list.add(el.getAsJsonObject());
            return list;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    /**
     * Find regions containing the given XZ coordinate.
     * @return matching regions, empty list on failure
     */
    public static List<JsonObject> findRegionsAt(int x, int z, String dimension) {
        String path = "/api/regions?x=" + x + "&z=" + z + "&dim=" + dimension;
        String resp = get(path);
        if (resp == null) return Collections.emptyList();
        try {
            JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();
            List<JsonObject> list = new java.util.ArrayList<>();
            for (JsonElement el : arr) list.add(el.getAsJsonObject());
            return list;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    /**
     * Delete a region by ID (owner-verified).
     * @return true if deleted
     */
    public static boolean deleteRegion(String id, String owner) {
        JsonObject body = new JsonObject();
        body.addProperty("owner", owner);
        return delete("/api/regions/" + id, body.toString());
    }

    // ─── HTTP primitives ───────────────────────────────────

    private static String get(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + path))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp.body();
            LOG.debug("GET {} returned {}", path, resp.statusCode());
            return null;
        } catch (Exception e) {
            LOG.debug("GET {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    private static String post(String path, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp.body();
            LOG.debug("POST {} returned {}", path, resp.statusCode());
            return null;
        } catch (Exception e) {
            LOG.debug("POST {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    private static boolean delete(String path, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            LOG.debug("DELETE {} failed: {}", path, e.getMessage());
            return false;
        }
    }
}
