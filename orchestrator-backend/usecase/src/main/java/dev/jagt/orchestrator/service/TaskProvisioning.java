package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * What belongs to the AGENT rather than to jagt stays behind {@link AgentRuntime}: this class never learns what a
 * given agent's config file is called.
 */
@Service
@RequiredArgsConstructor
public class TaskProvisioning {

    private final ConfigService configService;
    private final StateService stateService;
    private final NewTaskWorktrees worktrees;
    private final AgentSessions agentSessions;

    public String initializeTask(NewTask request) {
        String taskId = request.taskId();
        ConfigService.ConfigFile config = configService.load();
        boolean plan = AgentSessions.planMode(request.mode());
        BranchStrategy strategy = BranchStrategy.of(request.branchStrategy());
        List<NewRepo> repos;
        String alias;
        // The cap and the duplicate checks in cut() read what putTask writes.
        synchronized (this) {
            repos = worktrees.cut(request, config, strategy);
            alias = nextAlias(taskId);
            stateService.putTask(taskId, TaskState.builder(repos.stream().map(NewRepo::registered).toList(),
                            TaskStatus.NEW)
                    .lastActiveTimestamp(System.currentTimeMillis()).alias(alias)
                    .title(request.title())
                    .ticketUrl(request.ticketUrl() == null || request.ticketUrl().isBlank()
                            ? null : request.ticketUrl())
                    // Only the OVERRIDE is persisted: a task that took the project default must keep following it.
                    .baseBranch(NewTaskWorktrees.branchOverride(request.baseBranch()))
                    .autoReview(config.autoReview().enabledOrDefault())
                    .build());
        }
        NewRepo session = repos.get(0);

        try {
            agentSessions.startAgent(taskId, alias, session.worktreePath(), plan);
        } catch (RuntimeException e) {
            return "Task " + taskId + " registered and worktree created at " + session.worktreePath()
                    + ", but the agent session failed to start: " + e.getMessage()
                    + " Fix the cause and call open_task_tab(\"" + taskId + "\") — do NOT call initialize_task again.";
        }

        return taskId + " is " + alias + " — agent running on " + taskId
                + (strategy == BranchStrategy.RESUME ? " (resumed)" : " from " + session.baseBranch())
                + alsoIn(repos) + "."
                + (plan ? " Plan mode: approve its plan in the agent window (focus " + alias + ")." : "");
    }

    private static String alsoIn(List<NewRepo> repos) {
        return repos.size() < 2 ? "" : ", also in " + repos.stream().skip(1).map(NewRepo::project)
                .collect(Collectors.joining(", "));
    }

    private String nextAlias(String taskId) {
        String letter = taskId.substring(0, 1).toLowerCase(Locale.ROOT);
        var used = stateService.tasks().values().stream()
                .map(TaskState::alias)
                .filter(a -> a != null)
                .collect(Collectors.toSet());
        for (int i = 1; ; i++) {
            String candidate = letter + i;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
    }
}
