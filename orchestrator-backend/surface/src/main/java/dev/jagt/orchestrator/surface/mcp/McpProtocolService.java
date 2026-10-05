package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Schema;
import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

@Service
@Slf4j
public class McpProtocolService implements McpToolRegistry {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final long KEEP_ALIVE_THROTTLE_MS = 15_000;

    private record ToolSpec(String name, Audience audience, JsonNode schema, ToolHandler handler) {
    }

    private final ObjectMapper mapper;
    private final StateService stateService;
    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public McpProtocolService(ObjectMapper mapper, StateService stateService, List<McpTools> groups) {
        this.mapper = mapper;
        this.stateService = stateService;
        groups.forEach(group -> group.declare(this));
    }

    private void register(String name, Audience audience, String schemaJson, ToolHandler handler) {
        if (tools.putIfAbsent(name, new ToolSpec(name, audience, mapper.readTree(schemaJson), handler)) != null) {
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
                case "tools/list" -> toolsList(callerTaskId);
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
            log.atError().setMessage("mcp request failed")
                    .addKeyValue("method", method)
                    .addKeyValue("cause", e.toString())
                    .setCause(e)
                    .log();
            return isNotification ? Optional.empty() : Optional.of(error(id, -32603, describe(e)));
        }
    }

    public ObjectNode parseError(String message) {
        return error(mapper.nullNode(), -32700, "Parse error: " + message);
    }

    /** Any MCP traffic from a registered worktree proves the agent is alive. */
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

    private JsonNode toolsList(String callerTaskId) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode list = result.putArray("tools");
        tools.values().stream().filter(spec -> spec.audience().admits(callerTaskId)).forEach(spec -> {
            ObjectNode tool = list.addObject();
            tool.put("name", spec.name());
            tool.put("description", spec.schema().path("description").asText(""));
            ObjectNode schema = (ObjectNode) spec.schema().deepCopy();
            schema.remove("description");
            tool.set("inputSchema", schema);
        });
        return result;
    }

    /** The one place a message is read off the wire and judged, so no tool can be the one that forgot. */
    @Override
    public <T extends Message> void tool(String name, Audience audience, Schema schema, Class<T> message,
                                         BiFunction<T, String, MessageContext> context,
                                         MessageHandler<T> handler) {
        register(name, audience, schema.json(), MessageTool.of(mapper, name, audience, message, context, handler));
    }

    private JsonNode callTool(JsonNode message, String callerTaskId) {
        String name = message.path("params").path("name").asText("");
        JsonNode args = message.path("params").path("arguments");
        ToolSpec spec = tools.get(name);
        if (spec == null) {
            return failed(ToolFailure.VALIDATION, "Unknown tool: " + name);
        }
        try {
            return toolResult(spec.handler().call(args, callerTaskId));
        } catch (Exception e) {
            ToolFailure failure = ToolFailure.of(e);
            // A refused message is the caller's mistake, already answered on the wire, not a fault of jagt's.
            log.atLevel(failure == ToolFailure.VALIDATION ? Level.INFO : Level.WARN).setMessage("mcp tool failed")
                    .addKeyValue("tool", name)
                    .addKeyValue("note", failure.wire())
                    .addKeyValue("cause", e.toString())
                    .log();
            return failed(failure, describe(e));
        }
    }

    private ObjectNode toolResult(String text) {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", text);
        return result;
    }

    /** The category rides in the text too: a CLI may hand the model the text and drop the structured half. */
    private JsonNode failed(ToolFailure failure, String why) {
        ObjectNode result = toolResult("Error (" + failure.wire() + ", "
                + (failure.retryable() ? "retryable" : "not retryable") + "): " + why);
        result.put("isError", true);
        ObjectNode structured = result.putObject("structuredContent");
        structured.put("category", failure.wire());
        structured.put("retryable", failure.retryable());
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
