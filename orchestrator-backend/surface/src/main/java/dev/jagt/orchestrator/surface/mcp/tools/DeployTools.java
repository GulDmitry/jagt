package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.TaskRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DeployTools implements McpTools {

    private final CommandService commands;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("deploy_task", Audience.MASTER, TaskRef.schema("Merge the task's branch into the project's"
                        + " deployBranch (jagt.yml) and push it. On merge conflict nothing is pushed and the human"
                        + " resolves manually."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> commands.execute(said.taskId(), TaskAction.DEPLOY));

        tools.tool("revert_task", Audience.MASTER, TaskRef.schema("Undo a task's deploy: revert the merge commit it"
                        + " created on the deployBranch and push the revert. For a DEPLOYED or DEPLOY_CONFLICT task,"
                        + " or any with a deploy still live; a conflict is discarded. Refuses (nothing is written)"
                        + " when the commit is unknown, already reverted, or the revert conflicts."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> commands.execute(said.taskId(), TaskAction.REVERT));
    }
}
