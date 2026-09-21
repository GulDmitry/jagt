package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.SessionStart;
import dev.jagt.orchestrator.protocol.TaskInstructions;
import dev.jagt.orchestrator.protocol.TaskRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class SessionTools implements McpTools {

    private final AgentSessions sessions;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("open_task_tab", SessionStart.SCHEMA, SessionStart.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.openTaskTab(callerScope.resolve(said.taskId(), caller), said.mode()));

        tools.tool("close_task_tab", TaskRef.schema("Close a task's window and kill its agent session (e.g. when"
                        + " the task is finished). Worktree and state entry are kept — use remove_task to retire"
                        + " the task completely."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.closeTaskTab(callerScope.resolve(said.taskId(), caller)));

        tools.tool("focus_task", TaskRef.schema("Bring the task's agent window to the user's screen: select its"
                        + " window and raise the viewer. If the session was closed, a fresh one is started"
                        + " first."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.focusTask(callerScope.resolve(said.taskId(), caller)));

        tools.tool("write_task_context", TaskInstructions.SCHEMA, TaskInstructions.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.writeTaskContext(callerScope.resolve(said.taskId(), caller),
                        said.instructions()));
    }
}
