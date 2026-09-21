package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
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
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("deploy_task", TaskRef.schema("Merge the task's branch into the project's deployBranch"
                        + " (jagt.yml) and push it. On merge conflict nothing is pushed and the human resolves"
                        + " manually. Master-only."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> deploy(said.taskId(), caller));

        tools.tool("revert_task", TaskRef.schema("Undo a task's deploy: revert the merge commit it created on"
                        + " the deployBranch and push the revert. Only for a DEPLOYED task; refuses (nothing is"
                        + " written) when the commit is unknown, already reverted, or the revert conflicts."
                        + " Master-only."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> revert(said.taskId(), caller));
    }

    private String deploy(String taskId, String callerTaskId) {
        callerScope.requireMaster(callerTaskId, "deploy_task");
        return commands.execute(taskId, TaskAction.DEPLOY);
    }

    private String revert(String taskId, String callerTaskId) {
        callerScope.requireMaster(callerTaskId, "revert_task");
        return commands.execute(taskId, TaskAction.REVERT);
    }
}
