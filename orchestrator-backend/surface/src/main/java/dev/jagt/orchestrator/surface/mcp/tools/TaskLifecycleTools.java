package dev.jagt.orchestrator.surface.mcp.tools;

import dev.jagt.orchestrator.surface.mcp.Audience;
import dev.jagt.orchestrator.surface.mcp.McpToolRegistry;
import dev.jagt.orchestrator.surface.mcp.McpTools;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.capability.done.TaskRetirement;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.NewTaskMessage;
import dev.jagt.orchestrator.protocol.NoArguments;
import dev.jagt.orchestrator.protocol.TaskRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;



@Component
@RequiredArgsConstructor
public class TaskLifecycleTools implements McpTools {

    private final TaskProvisioning provisioning;
    private final TaskRetirement retirement;
    private final StateService stateService;
    private final ConfigService configService;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("initialize_task", Audience.MASTER, NewTaskMessage.SCHEMA, NewTaskMessage.class,
                (said, caller) -> MessageContext.NONE,
                (said, caller) -> provisioning.initializeTask(NewTask.builder(said.taskId(), said.projectKey())
                        .alsoIn(said.alsoProjects())
                        .instructions(said.instructions())
                        .mode(said.mode())
                        .branchStrategy(said.branchStrategy())
                        .baseBranch(said.baseBranch())
                        .title(said.title())
                        .ticketUrl(said.ticketUrl())
                        .build()));

        tools.tool("remove_task", Audience.MASTER, TaskRef.schema("Remove a finished or abandoned task: deletes its"
                        + " worktree and its state.json entry, keeping the branch."),
                TaskRef.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> retirement.retire(said.taskId()));

        tools.tool("list_tasks", Audience.ANYONE, NoArguments.schema("Return the full orchestrator state (all tasks,"
                        + " statuses, worktree paths) from state.json."),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> stateService.prettyJson());

        tools.tool("list_projects", Audience.MASTER, NoArguments.schema("Every project jagt works on, one per line:"
                        + " its key (what initialize_task's projectKey takes), what it is, and the labels placing an"
                        + " item in it."),
                NoArguments.class, (said, caller) -> MessageContext.NONE,
                (said, caller) -> configService.load().projects().entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue().aboutOrPath()
                                + " — labels " + entry.getValue().labels())
                        .collect(Collectors.joining("\n")));
    }
}
