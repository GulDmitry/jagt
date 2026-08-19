package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.service.IdeLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.surface.mcp.tools.ToolArgs.text;

@Component
@RequiredArgsConstructor
public class IdeTools implements McpTools {

    private final IdeLauncher ide;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("open_in_ide", """
                {
                  "description": "Open a task in IntelliJ. mode 'project' (default) opens the worktree as a full project (needed to run the app; use Git → Local Changes for a live diff vs base); mode 'diff' opens a STATIC snapshot diff vs base — it does NOT auto-refresh (re-run to update). taskId defaults to the calling worktree's task.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"},
                    "mode": {"type": "string", "enum": ["diff", "project"]}
                  }
                }""",
                (args, caller) -> ide.open(callerScope.resolve(text(args, "taskId"), caller), text(args, "mode")));
    }
}
