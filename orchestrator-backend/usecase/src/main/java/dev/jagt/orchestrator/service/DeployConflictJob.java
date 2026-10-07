package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.capability.deploy.DeployService;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the Master acts, a deploy conflict is the session's to resolve and the deploy jagt's to finish: the human
 * already pressed it, so the resolution staged in full is the trigger, not a second press.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeployConflictJob implements Job {

    private final ConfigService configService;
    private final DeployService deploys;
    private final AgentSessions sessions;
    private final CommandService commands;
    private final Map<String, String> asked = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "deploy-conflict";
    }

    @Override
    public String describe() {
        return "hand each deploy conflict to its session, and finish the deploy once it is resolved";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        if (configService.load().master().modeOrDefault() != MasterMode.ACT) {
            return;
        }
        deploys.conflicts().forEach((taskId, conflict) -> {
            if (conflict.resolved()) {
                asked.remove(taskId);
                log.atInfo().setMessage("master finishes deploy").addKeyValue("task", taskId)
                        .addKeyValue("worktree", conflict.worktree()).log();
                OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DEPLOY));
                return;
            }
            String brief = brief(taskId, conflict.worktree());
            if (!brief.equals(asked.get(taskId))) {
                sessions.relayIfChanged(taskId, brief);
                asked.put(taskId, brief);
            }
        });
    }

    private static String brief(String taskId, Path worktree) {
        return taskId + " did not deploy: merging it into the deploy branch conflicts in " + worktree + ".\n\n"
                + "Resolve it there, never in your own worktree, keeping what both sides meant. Build or test what"
                + " the project lets you, then `git add` every resolved path in one go: once nothing is unmerged"
                + " and nothing is left unstaged, jagt finishes the deploy. Do not commit, push or report a status."
                + " If a side cannot be kept without a human's call, report outcome=question.";
    }
}
