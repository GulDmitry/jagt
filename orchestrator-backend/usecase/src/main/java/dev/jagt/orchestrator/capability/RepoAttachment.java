package dev.jagt.orchestrator.capability;

import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NewTaskWorktrees;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class RepoAttachment {

    private final ConfigService configService;
    private final StateService stateService;
    private final NewTaskWorktrees worktrees;
    private final StepRelay relay;

    /** Cuts and registers the repository the task's session asked for, and tells the session where it is. */
    String attach(String taskId) {
        String id = stateService.canonicalTaskId(taskId);
        TaskState task = stateService.task(id).orElseThrow(() -> Refusal.noSuchTask(taskId));
        String project = task.repoRequest();
        if (project == null) {
            throw Refusal.byState("Task " + id + " asked for no repository");
        }
        NewRepo added = worktrees.attach(id, project, configService.load());
        log.atInfo().setMessage("repository added").addKeyValue("task", id).addKeyValue("project", project)
                .addKeyValue("path", added.worktreePath()).log();
        return told(id, added, Standing.of(task));
    }

    private String told(String taskId, NewRepo added, Standing read) {
        String where = added.project() + " is at " + added.worktreePath();
        try {
            relay.relayUnder(taskId, Step.REPO, "jagt added the repository you asked for: " + where + ", on branch " + taskId
                    + ", cut from " + added.baseBranch() + ".", false, read);
            return "add-repo " + taskId + ": " + where + "; the session was told";
        } catch (RuntimeException unheard) {
            log.atWarn().setMessage("session not told of repository").addKeyValue("task", taskId)
                    .addKeyValue("cause", unheard.toString())
                    .addKeyValue("effect", "the repository is added; the session learns it from its next brief").log();
            return "add-repo " + taskId + ": " + where + "; the session could not be told (" + unheard.getMessage()
                    + "), focus it";
        }
    }
}
