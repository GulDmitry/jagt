package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class StandBoard {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private static final Duration READING = Duration.ofSeconds(30);
    private static final Duration OPENING = Duration.ofMinutes(10);

    private final String base;

    public StandBoard(int port) {
        this.base = "http://127.0.0.1:" + port;
    }

    public boolean ready() {
        try {
            return send(HttpRequest.newBuilder(URI.create(base + "/state")).timeout(READING).GET()).statusCode() == 200;
        } catch (StandFailure down) {
            return false;
        }
    }

    public JsonNode open(String ref, String project, String mode) {
        String body = mapper.createObjectNode().put("ref", ref).put("project", project).put("mode", mode).toString();
        return post("/api/tasks", body, OPENING);
    }

    public JsonNode press(String task, String verb) {
        return post("/api/tasks/actions/" + verb + "?task=" + task, "{}", READING);
    }

    public JsonNode say(String task, String line) {
        return post("/api/tasks/say?task=" + task, mapper.createObjectNode().put("line", line).toString(), READING);
    }

    private JsonNode post(String path, String body, Duration timeout) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json").header("Origin", base).timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        if (response.statusCode() != 200) {
            throw new StandFailure(path + " answered " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }

    public JsonNode state() {
        return get("/state");
    }

    public JsonNode tasks() {
        return get("/api/tasks");
    }

    private JsonNode get(String path) {
        return mapper.readTree(send(HttpRequest.newBuilder(URI.create(base + path)).timeout(READING).GET()).body());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new StandFailure("jagt at " + base + " did not answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StandFailure("interrupted waiting for jagt at " + base, e);
        }
    }
}
