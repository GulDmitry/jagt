package dev.jagt.orchestrator.protocol;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * One message's fields, declared in Java and rendered as the JSON Schema a caller is given. Declared rather than
 * reflected: what a field MEANS to whoever fills it is the half a type cannot carry, and the compiler checks the
 * enum values against the machine that holds them.
 *
 * <p>The rendering is the only copy. A schema written out by hand beside the code that reads the fields drifts
 * from it silently, which is the duplication this replaces.
 */
public final class Schema {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final String description;
    private final ObjectNode properties = MAPPER.createObjectNode();
    private final List<String> required = new ArrayList<>();

    private Schema(String description) {
        this.description = description;
    }

    public static Schema of(String description) {
        return new Schema(description);
    }

    public Schema text(String name, String describes) {
        field(name, "string", describes);
        return this;
    }

    public Schema required(String name, String type, String describes) {
        field(name, type, describes);
        required.add(name);
        return this;
    }

    /** The values a field may carry, taken from whatever enumerates them, so the two cannot disagree. */
    public Schema choice(String name, Collection<?> values, String describes) {
        ObjectNode field = field(name, "string", describes);
        ArrayNode allowed = field.putArray("enum");
        values.forEach(value -> allowed.add(String.valueOf(value)));
        return this;
    }

    public Schema choiceRequired(String name, Collection<?> values, String describes) {
        choice(name, values, describes);
        required.add(name);
        return this;
    }

    /** A string-to-string object, with examples because a shape is easier shown than described. */
    public Schema pairs(String name, String describes, List<Map<String, String>> examples) {
        ObjectNode field = field(name, "object", describes);
        field.putObject("additionalProperties").put("type", "string");
        if (!examples.isEmpty()) {
            ArrayNode shown = field.putArray("examples");
            examples.forEach(example -> {
                ObjectNode entry = shown.addObject();
                example.forEach(entry::put);
            });
        }
        return this;
    }

    /** The fields a caller must send; the transport checks presence before anything else looks at the message. */
    public List<String> requiredFields() {
        return List.copyOf(required);
    }

    public String json() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("description", description);
        root.put("type", "object");
        root.set("properties", properties);
        ArrayNode names = root.putArray("required");
        required.forEach(names::add);
        return root.toPrettyString();
    }

    private ObjectNode field(String name, String type, String describes) {
        ObjectNode field = properties.putObject(name);
        field.put("type", type);
        if (describes != null && !describes.isBlank()) {
            field.put("description", describes);
        }
        return field;
    }
}
