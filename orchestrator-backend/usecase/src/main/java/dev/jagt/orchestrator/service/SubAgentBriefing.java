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

    private static final String NO_DEPLOY = "none here";

    private final PromptTemplates prompts;

    public String of(NewTask request, NewRepo repo, List<NewRepo> repos) {
        String taskId = request.taskId();
        return prompts.subAgentContext().formatted(
                taskId,
                taskId, repo.project(), repo.config().path(), repo.baseBranch(), repo.remoteUrl(),
                repo.worktreePath(),
                alsoYours(repo, repos),
                taskId, repo.baseBranch(), deployBranch(repo));
    }

    private static String deployBranch(NewRepo repo) {
        String branch = repo.config().deployBranch();
        return branch == null || branch.isBlank() ? NO_DEPLOY : "`" + branch + "`";
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
