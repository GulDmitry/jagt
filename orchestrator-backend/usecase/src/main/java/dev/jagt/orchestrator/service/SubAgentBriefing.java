package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.PromptTemplates;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** The system knowledge a fresh sub-agent wakes up with: its own task, and its branches. */
@Service
@RequiredArgsConstructor
public class SubAgentBriefing {

    private final PromptTemplates prompts;

    public String of(NewTask request, NewRepo repo, List<NewRepo> repos) {
        String taskId = request.taskId();
        return prompts.subAgentContext().formatted(
                taskId,
                taskId, repo.project(), repo.config().path(), repo.baseBranch(), repo.remoteUrl(),
                repo.worktreePath(),
                alsoYours(repo, repos),
                taskId, repo.baseBranch(), deployBranches(repos));
    }

    private static String deployBranches(List<NewRepo> repos) {
        List<NewRepo> deployed = repos.stream()
                .filter(repo -> repo.config().deployBranch() != null && !repo.config().deployBranch().isBlank())
                .toList();
        String named = deployed.stream()
                .map(repo -> "`" + repo.config().deployBranch() + "`"
                        + (repos.size() == 1 ? "" : " (" + repo.project() + ")"))
                .collect(Collectors.joining(", "));
        return switch (deployed.size()) {
            case 0 -> "";
            case 1 -> " The deploy branch " + named + " is jagt's `deploy` alone.";
            default -> " The deploy branches " + named + " are jagt's `deploy` alone.";
        };
    }

    /** The task's OTHER worktrees, which this agent may edit as well, or a sentence saying there are none. */
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
