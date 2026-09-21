package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.protocol.AgentStatusMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.UserNotice;
import dev.jagt.orchestrator.service.AgentStatusReports;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class StatusTools implements McpTools {

    private final AgentStatusReports statusReports;
    private final CallerScope callerScope;

    /** The task a report is about: the one it names where a Master sent it, else the caller's own worktree. */
    private String taskOf(AgentStatusMessage said, String caller) {
        return callerScope.resolve(said.taskId(), caller);
    }

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("update_agent_status", AgentStatusMessage.SCHEMA, AgentStatusMessage.class,
                (said, caller) -> statusReports.contextFor(taskOf(said, caller)),
                (said, caller) -> statusReports.report(said, taskOf(said, caller)));

        tools.tool("notify_user", UserNotice.SCHEMA, UserNotice.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> statusReports.notifyUser(said.title(), said.message()));
    }
}
