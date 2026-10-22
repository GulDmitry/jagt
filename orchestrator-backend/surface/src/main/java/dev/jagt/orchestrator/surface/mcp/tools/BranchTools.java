package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.protocol.BaseBranchMessage;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.service.TaskRetarget;
import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.surface.mcp.Effect;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BranchTools implements McpTools {

    private final TaskRetarget retarget;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("retarget_task", Audience.ANYONE, Effect.REPORTS, BaseBranchMessage.SCHEMA, BaseBranchMessage.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> retarget.retarget(callerScope.resolve(said.taskId(), caller),
                        Objects.requireNonNull(said.branch())));
    }
}
