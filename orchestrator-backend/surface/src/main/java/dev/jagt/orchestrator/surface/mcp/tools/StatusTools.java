package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.AgentStatusReports;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.surface.mcp.tools.ToolArgs.text;

@Component
@RequiredArgsConstructor
public class StatusTools implements McpTools {

    /** The allowed values come from the enum itself, so a new status cannot be missing from the schema. */
    private static final String STATUS_ENUM = java.util.Arrays.stream(TaskStatus.values())
            .map(status -> "\"" + status + "\"")
            .collect(java.util.stream.Collectors.joining(", "));

    private final AgentStatusReports statusReports;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("update_agent_status", """
                {
                  "description": "Update the task status and keep-alive timestamp in state.json. Sub-agents MUST call this frequently to avoid Watchdog alerts. taskId defaults to the calling worktree's task.",
                  "type": "object",
                  "properties": {
                    "status": {"type": "string", "enum": [%s]},
                    "message": {"type": "string", "description": "Progress note, 10 words MAX — it renders as one narrow dashboard table line (longer text is truncated)."},
                    "taskId": {"type": "string", "description": "Optional explicit task id or alias (Master use). Sub-agents may only target their own task."}
                  },
                  "required": ["status"]
                }""".formatted(STATUS_ENUM),
                (args, caller) -> statusReports.report(text(args, "status"), text(args, "message"),
                        callerScope.resolve(text(args, "taskId"), caller)));

        tools.tool("notify_user", """
                {
                  "description": "Send an OS push notification to the human (e.g. 'review round addressed — ABC-123'). Use when human attention is needed.",
                  "type": "object",
                  "properties": {
                    "title": {"type": "string", "description": "Defaults to 'jagt'."},
                    "message": {"type": "string"}
                  },
                  "required": ["message"]
                }""",
                (args, caller) -> statusReports.notifyUser(text(args, "title"), text(args, "message")));
    }
}
