package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.service.AgentSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.surface.mcp.tools.ToolArgs.text;

/** The agent session a task runs in: start it, kill it, look at it, talk to it. */
@Component
@RequiredArgsConstructor
public class SessionTools implements McpTools {

    private final AgentSessions sessions;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("open_task_tab", """
                {
                  "description": "Start a fresh sub-agent session (a terminal window) for an ALREADY registered task whose session is gone or unresponsive.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "mode": {"type": "string", "enum": ["auto", "plan"], "description": "plan = start the agent in its planning mode. Default: auto."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> sessions.openTaskTab(callerScope.resolve(text(args, "taskId"), caller),
                        text(args, "mode")));

        tools.tool("close_task_tab", """
                {
                  "description": "Close a task's window and kill its agent session (e.g. when the task is finished). Worktree and state entry are kept — use remove_task to retire the task completely.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> sessions.closeTaskTab(callerScope.resolve(text(args, "taskId"), caller)));

        tools.tool("focus_task", """
                {
                  "description": "Bring the task's agent window to the user's screen: select its window and raise the viewer. If the session was closed, a fresh one is started first.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id or its short alias (p1, s2, ...)."}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> sessions.focusTask(callerScope.resolve(text(args, "taskId"), caller)));

        tools.tool("write_task_context", """
                {
                  "description": "Write instructions to <worktree>/task_context.md of a task. Used by the Master's automated ship/review steps; not for ad-hoc human notes (the human talks to the agent directly in its tmux window).",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "instructions": {"type": "string"}
                  },
                  "required": ["taskId", "instructions"]
                }""",
                (args, caller) -> sessions.writeTaskContext(
                        callerScope.resolve(text(args, "taskId"), caller), text(args, "instructions")));
    }
}
