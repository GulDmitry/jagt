package dev.jagt.orchestrator.adapter.agent;

import tools.jackson.databind.node.ObjectNode;

final class OpenCodeProvider {

    private OpenCodeProvider() {
    }

    static void write(ObjectNode root, OpenCodeProperties opencode) {
        if (!opencode.hasEndpoint()) {
            return;
        }
        ObjectNode local = root.putObject("provider").putObject("local");
        local.put("npm", "@ai-sdk/openai-compatible");
        local.put("name", "local");
        local.putObject("options").put("baseURL", opencode.endpoint());
        local.putObject("models").putObject(opencode.model());
        root.put("model", "local/" + opencode.model());
    }
}
