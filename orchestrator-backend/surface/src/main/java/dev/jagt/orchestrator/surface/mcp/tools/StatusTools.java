package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.service.AgentStatusReports;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.surface.mcp.tools.ToolArgs.pairs;
import static dev.jagt.orchestrator.surface.mcp.tools.ToolArgs.text;

@Component
@RequiredArgsConstructor
public class StatusTools implements McpTools {

    private final AgentStatusReports statusReports;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("update_agent_status", AgentStatusMessage.SCHEMA.json(),
                (args, caller) -> statusReports.report(text(args, "status"), text(args, "message"),
                        text(args, "outcome"), text(args, "reviewRequestUrl"),
                        pairs(args, "reviewRequests"),
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
