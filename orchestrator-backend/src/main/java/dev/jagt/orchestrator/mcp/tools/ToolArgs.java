package dev.jagt.orchestrator.mcp.tools;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Blank is absent: the protocol checks required arguments for presence, and "" would pass that check. */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    /** An absent array and an empty one are the same answer, so no caller has to tell them apart. */
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
}
