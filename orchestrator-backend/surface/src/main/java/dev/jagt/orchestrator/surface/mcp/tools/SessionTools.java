package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.service.CommandService;
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
    private final CommandService commands;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("open_task_tab", Audience.MASTER, SessionStart.SCHEMA, SessionStart.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.openTaskTab(said.taskId(), said.mode()));

        tools.tool("close_task_tab", Audience.MASTER, TaskRef.schema("Close a task's tab and kill its agent"
                        + " session (e.g. when the task is finished). Worktree and state entry are kept: only"
                        + " the human's done retires a task."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.closeTaskTab(said.taskId()));

        tools.tool("focus_task", Audience.MASTER, TaskRef.schema("Bring the task's agent window to the user's"
                        + " screen: select its tab and raise the viewer. If the session was closed, a fresh one"
                        + " is started first."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> commands.execute(said.taskId(), TaskAction.FOCUS));

        tools.tool("write_task_context", Audience.MASTER, TaskInstructions.SCHEMA, TaskInstructions.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> sessions.writeTaskContext(said.taskId(),
                        said.instructions()));
    }
}
