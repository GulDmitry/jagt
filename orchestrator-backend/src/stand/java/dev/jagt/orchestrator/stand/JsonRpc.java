package dev.jagt.orchestrator.stand;

import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class JsonRpc {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final StandTools tools;

    public JsonRpc(StandTools tools) {
        this.tools = tools;
    }

    public Optional<String> handle(String body) {
        JsonNode message;
        try {
            message = mapper.readTree(body);
        } catch (JacksonException e) {
            return Optional.of(mapper.writeValueAsString(error(null, -32700, "Parse error")));
        }
        if (!message.has("id")) {
            return Optional.empty();
        }
        return Optional.of(mapper.writeValueAsString(respond(message)));
    }

    private ObjectNode respond(JsonNode message) {
        JsonNode id = message.get("id");
        JsonNode params = message.path("params");
        return switch (message.path("method").asString()) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, mapper.createObjectNode());
            case "tools/list" -> result(id, mapper.createObjectNode().set("tools", tools.definitions()));
            case "tools/call" -> result(id, call(params));
            default -> error(id, -32601, "Method not found: " + message.path("method").asString());
        };
    }

    private ObjectNode initialize(JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", params.path("protocolVersion").asString(DEFAULT_PROTOCOL_VERSION));
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo").put("name", "stand-host").put("version", "0.1.0");
        return result;
    }

    private ObjectNode call(JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        boolean failed = false;
        String text;
        try {
            text = mapper.writeValueAsString(tools.call(params.path("name").asString(), params.path("arguments")));
        } catch (StandFailure | JacksonException e) {
            failed = true;
            text = "cause: " + e.getMessage();
        }
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", failed);
        return result;
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0").set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0").set("id", id == null ? mapper.nullNode() : id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }
}
