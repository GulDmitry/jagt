package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.surface.mcp.tools.ToolArgs;
import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal MCP (JSON-RPC 2.0) server: initialize, ping, tools/list, tools/call.
 * The stdio transport lives in mcp_client.js which proxies each message here
 * over HTTP and injects the caller's CWD as X-Working-Directory.
 *
 * Each tool is declared exactly once as a ToolSpec (schema + handler), so
 * tools/list, dispatch and required-argument validation cannot drift apart.
 */
@Service
@Slf4j
public class McpProtocolService {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    /** Keep-alive writes are throttled: a bump within this window carries no information. */
    private static final long KEEP_ALIVE_THROTTLE_MS = 15_000;

    private record ToolSpec(String name, JsonNode schema, ToolHandler handler) {
    }

    private final ObjectMapper mapper;
    private final StateService stateService;
    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public McpProtocolService(ObjectMapper mapper, StateService stateService, List<McpTools> groups) {
        this.mapper = mapper;
        this.stateService = stateService;
        groups.forEach(group -> group.declare(this::register));
    }

    private void register(String name, String schemaJson, ToolHandler handler) {
        // Declarations are spread across groups now, so a collision is invisible unless it is loud.
        if (tools.putIfAbsent(name, new ToolSpec(name, mapper.readTree(schemaJson), handler)) != null) {
            throw new IllegalStateException("Two MCP tool groups both declare '" + name + "'");
        }
    }

    public Optional<JsonNode> handle(JsonNode message, String callerCwd) {
        String method = message.path("method").asText(null);
        JsonNode id = message.get("id");
        if (method == null) {
            // A response from the client to a server-initiated request; we never send those.
            return Optional.empty();
        }
        boolean isNotification = id == null || id.isNull();
        try {
            String callerTaskId = keepAlive(callerCwd);
            JsonNode result = switch (method) {
                case "initialize" -> initializeResult(message);
                case "ping" -> mapper.createObjectNode();
                case "tools/list" -> toolsList();
                case "tools/call" -> callTool(message, callerTaskId);
                default -> null;
            };
            if (isNotification) {
                return Optional.empty();
            }
            if (result == null) {
                return Optional.of(error(id, -32601, "Method not found: " + method));
            }
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", id);
            response.set("result", result);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("MCP {} failed: {}", method, e.getMessage(), e);
            return isNotification ? Optional.empty() : Optional.of(error(id, -32603, describe(e)));
        }
    }

    public ObjectNode parseError(String message) {
        return error(mapper.nullNode(), -32700, "Parse error: " + message);
    }

    /**
     * Any MCP traffic from a registered worktree proves the agent is alive
     * (documented contract), throttled so heartbeats don't rewrite state.json
     * several times per request.
     */
    private String keepAlive(String callerCwd) {
        var caller = stateService.findByWorktree(callerCwd);
        caller.ifPresent(entry -> {
            if (System.currentTimeMillis() - entry.getValue().lastActiveTimestamp() > KEEP_ALIVE_THROTTLE_MS) {
                stateService.updateTask(entry.getKey(), TaskState::touched);
            }
        });
        return caller.map(Map.Entry::getKey).orElse(null);
    }

    private JsonNode initializeResult(JsonNode message) {
        String requestedVersion = message.path("params").path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", requestedVersion);
        result.putObject("capabilities").putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "jagt-orchestrator");
        serverInfo.put("version", "0.1.0");
        return result;
    }

    private JsonNode toolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode list = result.putArray("tools");
        tools.values().forEach(spec -> {
            ObjectNode tool = list.addObject();
            tool.put("name", spec.name());
            tool.put("description", spec.schema().path("description").asText(""));
            ObjectNode schema = (ObjectNode) spec.schema().deepCopy();
            schema.remove("description");
            tool.set("inputSchema", schema);
        });
        return result;
    }

    private JsonNode callTool(JsonNode message, String callerTaskId) {
        String name = message.path("params").path("name").asText("");
        JsonNode args = message.path("params").path("arguments");
        ToolSpec spec = tools.get(name);
        if (spec == null) {
            return toolResult("Error: Unknown tool: " + name, true);
        }
        for (JsonNode required : spec.schema().path("required")) {
            if (ToolArgs.text(args, required.asText()) == null) {
                return toolResult("Error: Argument '" + required.asText() + "' is required", true);
            }
        }
        try {
            return toolResult(spec.handler().call(args, callerTaskId), false);
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", name, e.getMessage());
            return toolResult("Error: " + describe(e), true);
        }
    }

    private JsonNode toolResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        if (isError) {
            result.put("isError", true);
        }
        return result;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}
