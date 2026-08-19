package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** The system knowledge a fresh sub-agent wakes up with: its own task, and where everything else lives. */
@Service
@RequiredArgsConstructor
public class SubAgentBriefing {

    private final PromptTemplates prompts;
    private final OrchestratorProperties properties;
    private final OrchestratorPaths paths;
    private final ConfigService configService;
    private final StateService stateService;

    public String of(NewTask request, NewRepo repo, List<NewRepo> repos) {
        String taskId = request.taskId();
        var projects = configService.load().projects();
        String projectsTable = projects.entrySet().stream()
                .map(e -> "| " + e.getKey() + " | " + e.getValue().path() + " | " + e.getValue().baseBranch() + " |")
                .collect(Collectors.joining("\n"));
        String activeTasks = stateService.tasks().entrySet().stream()
                .map(e -> "- " + e.getKey() + " [" + e.getValue().status() + "] " + e.getValue().worktreePath())
                .collect(Collectors.joining("\n"));
        return prompts.subAgentContext().formatted(
                taskId,
                taskId, repo.project(), repo.config().path(), repo.baseBranch(), repo.remoteUrl(),
                repo.worktreePath(),
                alsoYours(repo, repos),
                properties.watchdog().staleAfter().toMinutes() + " minutes",
                taskId,
                taskId, repo.baseBranch(),
                paths.root(),
                paths.stateFile(),
                paths.configFile(),
                projectsTable.isBlank() ? "| (none) | | |" : projectsTable,
                activeTasks.isBlank() ? "- (none)" : activeTasks);
    }

    /**
     * The task's OTHER worktrees, which this agent may edit as well — one line each, or a sentence saying there
     * are none. Without it the agent reads the one-worktree rule and refuses the very work it was given.
     */
    private static String alsoYours(NewRepo mine, List<NewRepo> repos) {
        String siblings = repos.stream()
                .filter(repo -> !repo.project().equals(mine.project()))
                .map(repo -> "  - " + repo.project() + ": " + repo.worktreePath()
                        + " (cut from " + repo.baseBranch() + ")")
                .collect(Collectors.joining("\n"));
        return siblings.isBlank()
                ? "- This task works in this repository only."
                : "- This task ALSO works in these worktrees, and you edit them yourself — same task, same"
                        + " branch name, one session:\n" + siblings
                        + "\n  Your instructions and your drafted review replies stay in THIS directory:"
                        + " task_context.md and review_replies.md are read from here, never from those.";
    }
}
