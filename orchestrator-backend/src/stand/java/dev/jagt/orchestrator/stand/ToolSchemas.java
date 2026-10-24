package dev.jagt.orchestrator.stand;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class ToolSchemas {

    private ToolSchemas() {
    }

    static ObjectNode tool(String name, String description, String required, String optional) {
        ObjectNode tool = JsonMapper.builder().build().createObjectNode();
        tool.put("name", name).put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode requiredNames = schema.putArray("required");
        for (String entry : split(required)) {
            properties.putObject(entry.split(":")[0]).put("type", entry.split(":")[1]);
            requiredNames.add(entry.split(":")[0]);
        }
        for (String entry : split(optional)) {
            properties.putObject(entry.split(":")[0]).put("type", entry.split(":")[1]);
        }
        return tool;
    }

    private static String[] split(String spec) {
        return spec.isEmpty() ? new String[0] : spec.split(",");
    }
}
