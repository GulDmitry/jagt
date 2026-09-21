package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.surface.mcp.CallerScope;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.capability.done.TaskRetirement;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.NewTaskMessage;
import dev.jagt.orchestrator.protocol.NoArguments;
import dev.jagt.orchestrator.protocol.TaskRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;



@Component
@RequiredArgsConstructor
public class TaskLifecycleTools implements McpTools {

    private final TaskProvisioning provisioning;
    private final TaskRetirement retirement;
    private final StateService stateService;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("initialize_task", NewTaskMessage.SCHEMA, NewTaskMessage.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> initialize(caller, NewTask.builder(said.taskId(), said.projectKey())
                        .alsoIn(said.alsoProjects())
                        .instructions(said.instructions())
                        .mode(said.mode())
                        .branchStrategy(said.branchStrategy())
                        .baseBranch(said.baseBranch())
                        .title(said.title())
                        .ticketUrl(said.ticketUrl())
                        .build()));

        tools.tool("remove_task", TaskRef.schema("Remove a finished or abandoned task: deletes its worktree and"
                        + " its state.json entry, keeping the branch. Master-only."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> retire(said.taskId(), caller));

        tools.tool("list_tasks", NoArguments.schema("Return the full orchestrator state (all tasks, statuses,"
                        + " worktree paths) from state.json."),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> stateService.prettyJson());
    }

    private String retire(String taskId, String callerTaskId) {
        callerScope.requireMaster(callerTaskId, "remove_task");
        return retirement.retire(taskId);
    }

    private String initialize(String callerTaskId, NewTask request) {
        callerScope.requireMaster(callerTaskId, "initialize_task");
        return provisioning.initializeTask(request);
    }
}
