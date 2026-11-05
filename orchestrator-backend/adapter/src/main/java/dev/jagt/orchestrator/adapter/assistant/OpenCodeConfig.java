package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.OpenCodeProvider;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The config one read runs under: a single agent offered only the read tools, every other door denied first. The
 * doors sit on the agent: a custom agent ignores the top-level {@code permission}.
 */
final class OpenCodeConfig {

    static final String AGENT = "jagt-read";
    private static final JsonMapper JSON = new JsonMapper();

    private OpenCodeConfig() {
    }

    static String json(String endpoint, String model, OpenCodeScope scope) {
        ObjectNode root = JSON.createObjectNode();
        root.put("$schema", "https://opencode.ai/config.json");
        root.put("autoupdate", false);
        root.put("share", "disabled");
        OpenCodeProvider.write(root, endpoint, model);
        root.put("default_agent", AGENT);
        ObjectNode agent = root.putObject("agent").putObject(AGENT);
        ObjectNode tools = agent.putObject("tools");
        tools.put("*", false);
        tools.put("read", true);
        tools.put("grep", true);
        tools.put("glob", true);
        tools.put("bash", true);
        if (scope.fetch()) {
            tools.put("webfetch", true);
        }
        ObjectNode permission = agent.putObject("permission");
        permission.put("edit", "deny");
        permission.put("webfetch", scope.fetch() ? "allow" : "deny");
        ObjectNode outside = permission.putObject("external_directory");
        outside.put("*", "deny");
        scope.roots().forEach(dir -> outside.put(dir + "/**", "allow"));
        ObjectNode bash = permission.putObject("bash");
        bash.put("*", "deny");
        scope.bashAllowed().forEach(glob -> bash.put(glob, "allow"));
        scope.bashRefused().forEach(glob -> bash.put(glob, "deny"));
        bash.put("*>*", "deny");
        return JSON.writeValueAsString(root);
    }
}
