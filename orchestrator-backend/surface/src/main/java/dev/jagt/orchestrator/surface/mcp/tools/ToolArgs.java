package dev.jagt.orchestrator.surface.mcp.tools;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blank is absent: the protocol checks required arguments for presence, and "" would pass that check. */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /** An absent array and an empty one are the same answer. */
    public static List<String> texts(JsonNode args, String field) {
        JsonNode value = args.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isNull() && !item.asText().isBlank()) {
                items.add(item.asText());
            }
        });
        return List.copyOf(items);
    }

    public static Map<String, String> pairs(JsonNode args, String field) {
        JsonNode value = args.path(field);
        if (!value.isObject()) {
            return Map.of();
        }
        Map<String, String> pairs = new LinkedHashMap<>();
        value.propertyStream().forEach(property -> {
            if (!property.getValue().isNull() && !property.getValue().asText().isBlank()) {
                pairs.put(property.getKey(), property.getValue().asText());
            }
        });
        return Collections.unmodifiableMap(pairs);
    }
}
