package dev.jagt.orchestrator.mcp.tools;

import tools.jackson.databind.JsonNode;

/** Blank is absent: the protocol checks required arguments for presence, and "" would pass that check. */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
