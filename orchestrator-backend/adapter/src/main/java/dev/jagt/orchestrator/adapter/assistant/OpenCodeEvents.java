package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.task.TokenUsage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** What one {@code opencode run --format json} printed: the last text it said, the tokens it spent, the tools it called. */
record OpenCodeEvents(Optional<String> answer, TokenUsage usage, List<Call> calls) {

    record Call(String tool, String status, JsonNode input) {
    }

    private static final JsonMapper MAPPER = new JsonMapper();

    static OpenCodeEvents parse(String stdout) {
        String answer = null;
        TokenUsage usage = TokenUsage.NONE;
        List<Call> calls = new ArrayList<>();
        for (String line : stdout.split("\n", 0)) {
            JsonNode event = event(line);
            JsonNode part = event.path("part");
            switch (event.path("type").asString("")) {
                case "text" -> answer = part.path("text").asString("");
                case "tool_use" -> calls.add(new Call(part.path("tool").asString(""),
                        part.path("state").path("status").asString(""), part.path("state").path("input")));
                case "step_finish" -> usage = usage.plus(stepUsage(part));
                default -> { }
            }
        }
        return new OpenCodeEvents(Optional.ofNullable(answer), usage, calls);
    }

    private static TokenUsage stepUsage(JsonNode part) {
        JsonNode tokens = part.path("tokens");
        JsonNode cache = tokens.path("cache");
        long fresh = tokens.path("input").asLong(0) + cache.path("write").asLong(0);
        long output = tokens.path("output").asLong(0) + tokens.path("reasoning").asLong(0);
        return part.has("cost")
                ? TokenUsage.ofCall(fresh, cache.path("read").asLong(0), output, part.path("cost").asDouble(0))
                : TokenUsage.ofUnpricedCall(fresh, cache.path("read").asLong(0), output);
    }

    private static JsonNode event(String line) {
        try {
            return MAPPER.readTree(line);
        } catch (RuntimeException unparseable) {
            return MAPPER.createObjectNode();
        }
    }
}
