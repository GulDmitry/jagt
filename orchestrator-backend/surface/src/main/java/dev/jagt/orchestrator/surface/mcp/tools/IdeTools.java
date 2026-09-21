package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.service.IdeLauncher;
import dev.jagt.orchestrator.protocol.IdeOpen;
import dev.jagt.orchestrator.protocol.MessageContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class IdeTools implements McpTools {

    private final IdeLauncher ide;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("open_in_ide", IdeOpen.SCHEMA, IdeOpen.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> ide.open(callerScope.resolve(said.taskId(), caller), said.mode()));
    }
}
