package dev.jagt.orchestrator.mcp.tools;

import dev.jagt.orchestrator.mcp.McpToolRegistry;
import dev.jagt.orchestrator.mcp.McpTools;
import dev.jagt.orchestrator.mcp.CallerScope;
import dev.jagt.orchestrator.model.NewTask;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskProvisioning;
import dev.jagt.orchestrator.service.TaskRetirement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static dev.jagt.orchestrator.mcp.tools.ToolArgs.text;
import static dev.jagt.orchestrator.mcp.tools.ToolArgs.texts;

/** A task from nothing to nothing: created, listed, retired. */
@Component
@RequiredArgsConstructor
public class TaskLifecycleTools implements McpTools {

    private final TaskProvisioning provisioning;
    private final TaskRetirement retirement;
    private final StateService stateService;
    private final CallerScope callerScope;

    @Override
    public void declare(McpToolRegistry tools) {
        tools.tool("initialize_task", """
                {
                  "description": "Create an isolated Git worktree for a task, register it in state.json and start a Claude sub-agent in a tmux window. Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string", "description": "Task id, e.g. ABC-123 (letters, digits, - and _). Becomes the branch name, worktree prefix and tmux window name."},
                    "projectKey": {"type": "string", "description": "Project key from config.json — where the agent's session runs."},
                    "alsoProjects": {"type": "array", "items": {"type": "string"}, "description": "Further project keys the SAME session works in: one worktree each, one branch name, one review round per repository. For a change that spans repositories (a service and its client); omit for ordinary work."},
                    "instructions": {"type": "string", "description": "Optional initial instructions, written to task_context.md in the new worktree."},
                    "title": {"type": "string", "description": "The Jira ticket title (shown in the dashboard while the task is in development). Fetch it when delegating."},
                    "ticketUrl": {"type": "string", "description": "Canonical web link to the ticket (shown as the dashboard's clickable ticket line). Fetch it when delegating."},
                    "mode": {"type": "string", "enum": ["auto", "plan"], "description": "plan = the agent starts in Claude plan mode (plans first, human approves in its tmux window). Default: auto."},
                    "branchStrategy": {"type": "string", "enum": ["fresh", "recreate", "resume"], "description": "For reopened tickets whose branch still exists: recreate = delete it and branch fresh from base (previous request merged), resume = continue the existing branch and its commits (unmerged work). Default fresh = error if the branch exists."},
                    "baseBranch": {"type": "string", "description": "Branch to cut the worktree from and to target with the review request, e.g. a parent feature branch. Must exist on origin. Default: the project's configured baseBranch."}
                  },
                  "required": ["taskId", "projectKey"]
                }""",
                (args, caller) -> initialize(caller,
                        NewTask.builder(text(args, "taskId"), text(args, "projectKey"))
                                .alsoIn(texts(args, "alsoProjects"))
                                .instructions(text(args, "instructions"))
                                .mode(text(args, "mode"))
                                .branchStrategy(text(args, "branchStrategy"))
                                .baseBranch(text(args, "baseBranch"))
                                .title(text(args, "title"))
                                .ticketUrl(text(args, "ticketUrl"))
                                .build()));

        tools.tool("remove_task", """
                {
                  "description": "Remove a finished/abandoned task: deletes its worktree and its state.json entry (the branch is kept). Master-only.",
                  "type": "object",
                  "properties": {
                    "taskId": {"type": "string"}
                  },
                  "required": ["taskId"]
                }""",
                (args, caller) -> retire(text(args, "taskId"), caller));

        tools.tool("list_tasks", """
                {
                  "description": "Return the full orchestrator state (all tasks, statuses, worktree paths) from state.json.",
                  "type": "object",
                  "properties": {}
                }""",
                (args, caller) -> stateService.prettyJson());
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
