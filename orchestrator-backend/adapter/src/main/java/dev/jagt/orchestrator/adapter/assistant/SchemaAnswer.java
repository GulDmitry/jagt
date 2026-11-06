package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.service.OneLine;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
final class SchemaAnswer {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final JsonMapper MAPPER = new JsonMapper();

    private SchemaAnswer() {
    }

    /** {@code structured_output} when the CLI already parsed it, else {@code result}, holding the same JSON. */
    static Optional<JsonNode> answerOf(JsonNode envelope, String label, String schema) {
        return objectOf(envelope, label, schema).flatMap(answer -> withBooleans(answer, label, schema));
    }

    /** A model writes "True" for a boolean as often as true: a text of true or false either way is read, any other word is no answer. */
    private static Optional<JsonNode> withBooleans(JsonNode answer, String label, String schema) {
        ObjectNode object = (ObjectNode) answer;
        for (var property : MAPPER.readTree(schema).path("properties").properties()) {
            JsonNode value = object.get(property.getKey());
            if (value == null || !"boolean".equals(property.getValue().path("type").asString(""))
                    || value.isBoolean()) {
                continue;
            }
            String text = value.isString() ? value.asString("") : "";
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                return outsideSchema(label, property.getKey() + " is not a boolean", answer.toString());
            }
            object.put(property.getKey(), text.equalsIgnoreCase("true"));
        }
        return Optional.of(answer);
    }

    private static Optional<JsonNode> objectOf(JsonNode envelope, String label, String schema) {
        JsonNode structured = envelope.path("structured_output");
        if (structured.isObject()) {
            return Optional.of(structured);
        }
        String raw = envelope.path("result").asString("");
        if (raw.isBlank()) {
            log.atWarn().setMessage("assistant answer empty")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", "result blank")
                    .log();
            return Optional.empty();
        }
        try {
            JsonNode answer = MAPPER.readTree(fenced(raw));
            return answer.isObject() && carriesSchema(answer, schema)
                    ? Optional.of(answer)
                    : outsideSchema(label, "not the schema's object", raw);
        } catch (RuntimeException e) {
            return outsideSchema(label, e.getMessage(), raw);
        }
    }

    /** A model that skipped the schema tool often still writes the object, fenced inside its prose: the last fence. */
    private static String fenced(String raw) {
        Matcher fence = FENCE.matcher(raw);
        String last = raw;
        while (fence.find()) {
            last = fence.group(1);
        }
        return last;
    }

    /**
     * The CLI validated {@code structured_output} against the schema; this fallback nobody checked. The
     * schema's FIRST required field is its discriminator, so an answer built of generic names alone
     * ({@code title}, {@code url}) cannot pass as facts and be read as "no such item".
     */
    private static boolean carriesSchema(JsonNode answer, String schema) {
        String discriminator = MAPPER.readTree(schema).path("required").path(0).asString("");
        return !discriminator.isEmpty() && answer.has(discriminator);
    }

    /** An answer read as facts would report a read that never happened as the item not existing. */
    private static Optional<JsonNode> outsideSchema(String label, @Nullable String cause, String said) {
        log.atWarn().setMessage("assistant answered outside the schema")
                .addKeyValue("ref", label)
                .addKeyValue("cause", oneLine(cause))
                .addKeyValue("said", oneLine(said))
                .log();
        return Optional.empty();
    }

    private static @Nullable String oneLine(@Nullable String value) {
        return OneLine.clipped(value == null ? "" : value.replace('"', '\''));
    }
}
