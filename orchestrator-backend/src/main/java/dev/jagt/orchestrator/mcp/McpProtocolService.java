package dev.jagt.orchestrator.mcp;

import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.model.TaskState;
import dev.jagt.orchestrator.model.TaskStatus;
import dev.jagt.orchestrator.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @FunctionalInterface
    private interface ToolHandler {
        String call(JsonNode args, String callerTaskId);
    }

    private record ToolSpec(String name, JsonNode schema, ToolHandler handler) {
    }

    private final ObjectMapper mapper;
    private final StateService stateService;
    private final Map<String, ToolSpec> tools = new LinkedHashMap<>();

    public McpProtocolService(ObjectMapper mapper, OrchestratorTools orchestrator, StateService stateService) {
        this.mapper = mapper;
        this.stateService = stateService;
        String statusEnum = Arrays.stream(TaskStatus.values())
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", "));

        register("initialize_task", """
                {
                  "description": "Create an isolated Git worktree for a task, register it in state.json and start a Claude sub-agent in a tmux window. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id, e.g. ABC-123 (letters, digits, - and _). Becomes the branch name, worktree prefix and tmux window name."},
                    "projectKey": {"type": "string", "description": "Project key from config.json."},
                    "instructions": {"type": "string", "description": "Optional initial instructions, written to task_context.md in the new worktree."},
                    "title": {"type": "string", "description": "The Jira ticket title (shown in the dashboard while the task is in development). Fetch it when delegating."},
                    "ticketUrl": {"type": "string", "description": "Canonical web link to the ticket (shown as the dashboard's clickable ticket line). Fetch it when delegating."},
                    "mode": {"type": "string", "enum": ["auto", "plan"], "description": "plan = the agent starts in Claude plan mode (plans first, human approves in its tmux window). Default: auto."},
                    "branchStrategy": {"type": "string", "enum": ["fresh", "recreate", "resume"], "description": "For reopened tickets whose branch still exists: recreate = delete it and branch fresh from base (previous MR merged), resume = continue the existing branch and its commits (unmerged work). Default fresh = error if the branch exists."},
                    "baseBranch": {"type": "string", "description": "Branch to cut the worktree from and to target with the review request, e.g. a parent feature branch. Must exist on origin. Default: the project's configured baseBranch."}
                  },
                  "required": ["taskId", "projectKey"]
                }""",
                (args, caller) -> orchestrator.initializeTask(
                        NewTask.builder(text(args, "taskId"), text(args, "projectKey"))
                                .instructions(text(args, "instructions"))
                                .mode(text(args, "mode"))
                                .branchStrategy(text(args, "branchStrategy"))
                                .baseBranch(text(args, "baseBranch"))
                                .title(text(args, "title"))
                                .ticketUrl(text(args, "ticketUrl"))
                                .build()));

        register("update_agent_status", """
                {
                  "description": "Update the task status and keep-alive timestamp in state.json. Sub-agents MUST call this frequently to avoid Watchdog alerts. taskId defaults to the calling worktree's task.",
                  "type": "object",
                  "properties": {
                    "status": {"type": "string", "enum": [%s]},
                    "message": {"type": "string", "description": "Progress note, 10 words MAX — it renders as one narrow dashboard table line (longer text is truncated)."},
                    "taskId": {"type": "string", "description": "Optional explicit task id or alias (Master use). Sub-agents may only target their own task."}
                  },
                  "required": ["status"]
                }""".formatted(statusEnum),
                (args, caller) -> orchestrator.updateAgentStatus(
                        text(args, "status"), text(args, "message"), text(args, "taskId"), caller));

        register("open_task_tab", """
                {
                  "description": "Start a fresh Claude sub-agent session (tmux window) for an ALREADY registered task whose session is gone or unresponsive.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "mode": {"type": "string", "enum": ["auto", "plan"], "description": "plan = start in Claude plan mode. Default: auto."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.openTaskTab(text(args, "taskId"), text(args, "mode")));

        register("open_in_ide", """
                {
                  "description": "Open a task in IntelliJ. mode 'project' (default) opens the worktree as a full project (needed to run the app; use Git → Local Changes for a live diff vs base); mode 'diff' opens a STATIC snapshot diff vs base — it does NOT auto-refresh (re-run to update). taskId defaults to the calling worktree's task.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "mode": {"type": "string", "enum": ["diff", "project"]}
                  }
                }""",
                (args, caller) -> orchestrator.openInIde(text(args, "taskId"), text(args, "mode"), caller));

        register("write_task_context", """
                {
                  "description": "Write instructions to <worktree>/task_context.md of a task. Used by the Master's automated ship/review steps; not for ad-hoc human notes (the human talks to the agent directly in its tmux window).",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "instructions": {"type": "string"}
                  },
                  "required": ["taskId", "instructions"]
                }""",
                (args, caller) -> orchestrator.writeTaskContext(
                        text(args, "taskId"), text(args, "instructions")));

        register("close_task_tab", """
                {
                  "description": "Close a task's tmux window and kill its Claude session (e.g. when the task is finished). Worktree and state entry are kept — use remove_task to retire the task completely.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.closeTaskTab(text(args, "taskId"), caller));

        register("remove_task", """
                {
                  "description": "Remove a finished/abandoned task: deletes its worktree and its state.json entry (the branch is kept). Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.removeTask(text(args, "taskId"), caller));

        register("deploy_task", """
                {
                  "description": "Merge the task's branch into the project's deployBranch (config.json) and push it. On merge conflict nothing is pushed and the human resolves manually. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.deployTask(text(args, "taskId"), caller));

        register("revert_task", """
                {
                  "description": "Undo a task's deploy: revert the merge commit it created on the deployBranch and push the revert. Only for a DEPLOYED task; refuses (nothing is written) when the commit is unknown, already reverted, or the revert conflicts. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.revertTask(text(args, "taskId"), caller));

        register("focus_task", """
                {
                  "description": "Bring the task's agent window to the user's screen: switch tmux to its window and bring Warp to the foreground. If the session was closed, a fresh Claude session is started first.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias (p1, s2, ...)."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> orchestrator.focusTask(text(args, "taskId")));

        register("notify_user", """
                {
                  "description": "Send an OS push notification to the human (e.g. 'review round addressed — ABC-123'). Use when human attention is needed.",
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "Defaults to 'jagt'."},
                    "message": {"type": "string"}
                  },
                  "required": ["message"]
                }""",
                (args, caller) -> orchestrator.notifyUser(text(args, "title"), text(args, "message")));

        register("list_tasks", """
                {
                  "description": "Return the full orchestrator state (all tasks, statuses, worktree paths) from state.json.",
                  "type": "object",
                  "properties": {}
                }""",
                (args, caller) -> orchestrator.listTasks());
    }

    private void register(String name, String schemaJson, ToolHandler handler) {
        tools.put(name, new ToolSpec(name, mapper.readTree(schemaJson), handler));
    }

    public Optional<JsonNode> handle(JsonNode message, String callerCwd) {
        String method = message.path("method").asText(null);
        JsonNode id = message.get("id");
        if (method == null) {
            // A response from the client to a server-initiated request; we never send those.
            return Optional.empty();
        }
        boolean isNotification = id == null || id.isNull();
        String callerTaskId = keepAlive(callerCwd);
        try {
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
            return isNotification ? Optional.empty() : Optional.of(error(id, -32603, e.getMessage()));
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
            if (text(args, required.asText()) == null) {
                return toolResult("Error: Argument '" + required.asText() + "' is required", true);
            }
        }
        try {
            return toolResult(spec.handler().call(args, callerTaskId), false);
        } catch (Exception e) {
            log.warn("Tool {} failed: {}", name, e.getMessage());
            return toolResult("Error: " + e.getMessage(), true);
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
        error.put("message", message == null ? "Internal error" : message);
        return response;
    }

    private static String text(JsonNode args, String field) {
        JsonNode value = args.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
