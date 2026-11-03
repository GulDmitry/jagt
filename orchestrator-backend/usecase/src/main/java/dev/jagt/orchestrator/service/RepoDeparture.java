package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** What a repository leaving a task leaves behind: its checkout goes, its branch stays, the session is told. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RepoDeparture {

    private final ConfigService configService;
    private final RetiredWorktrees worktrees;
    private final AgentSessions sessions;

    public void settle(String taskId, TaskRepo repo, String cause) {
        log.atInfo().setMessage("repository left the task").addKeyValue("task", taskId)
                .addKeyValue("project", repo.project()).addKeyValue("request", repo.mrUrl())
                .addKeyValue("cause", cause).log();
        try {
            worktrees.remove(taskId, List.of(repo), configService.load().projects());
        } catch (RuntimeException failed) {
            log.atWarn().setMessage("checkout of repository not removed").addKeyValue("task", taskId)
                    .addKeyValue("project", repo.project()).addKeyValue("cause", failed.toString())
                    .addKeyValue("effect", "the repository is out of the task; its checkout stays on disk").log();
        }
        try {
            sessions.relayUnder(taskId, "jagt took " + repo.project() + " out of this task: " + cause
                    + ". Its branch " + taskId + " is kept; do not work there.", false);
        } catch (RuntimeException unheard) {
            log.atWarn().setMessage("session not told of repository leaving").addKeyValue("task", taskId)
                    .addKeyValue("project", repo.project()).addKeyValue("cause", unheard.toString())
                    .addKeyValue("effect", "the repository is out of the task; the session learns it from its next brief").log();
        }
    }
}
