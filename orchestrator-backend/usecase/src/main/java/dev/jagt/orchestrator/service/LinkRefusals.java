package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Reach;
import dev.jagt.orchestrator.port.ReachLost;
import dev.jagt.orchestrator.protocol.Reported;
import dev.jagt.orchestrator.task.ForeignLinks;
import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What a refused review-request link leaves behind: a line on the board's head and one nudge to the reporting session. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LinkRefusals {

    static final String DRIVER = "review request";

    private final Reach reach;
    private final SessionLines sessions;
    private final StateService stateService;

    /** Called once the report is applied. */
    public void after(Reported reported, String taskId) {
        Optional<TaskState> task = stateService.task(taskId);
        if (task.isEmpty()) {
            return;
        }
        ForeignLinks.Judged judged = ForeignLinks.judge(task.get(), reported.requests(), reported.link());
        judged.byProject().keySet().forEach(project -> reach.reached(DRIVER, host(taskId, project)));
        String primary = task.get().primary().project();
        if (judged.byProject().isEmpty() && judged.url() != null && primary != null) {
            reach.reached(DRIVER, host(taskId, primary));
        }
        judged.foreign().forEach(foreign -> reach.lost(new ReachLost(DRIVER, host(taskId, foreign.project()), List.of(),
                said(task.get(), foreign)), () -> standing(taskId, foreign.project())));
        if (!judged.foreign().isEmpty()) {
            nudge(taskId, judged.foreign());
        }
    }

    private boolean standing(String taskId, String project) {
        return stateService.task(taskId).flatMap(task -> task.repo(project)).map(repo -> !linkedWell(repo)).orElse(false);
    }

    private static boolean linkedWell(TaskRepo repo) {
        return repo.hasReviewRequest() && GitRemote.holds(repo.remoteUrl(), Objects.requireNonNull(repo.mrUrl()));
    }

    private void nudge(String taskId, List<ForeignLinks.Foreign> foreign) {
        String line = "jagt dropped " + foreign.stream().map(one -> one.project() + "'s link " + one.url()
                        + " (expected under " + GitRemote.projectPath(one.remote()) + ")")
                .reduce((a, b) -> a + "; " + b).orElse("")
                + ". Link the request that lives in the repository's own remote.";
        try {
            sessions.say(taskId, line);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("link refusal not said").addKeyValue("task", taskId)
                    .addKeyValue("cause", Causes.of(e)).addKeyValue("effect", "the board's head still says it").log();
        }
    }

    private static String said(TaskState task, ForeignLinks.Foreign foreign) {
        return (task.alias() == null ? "" : task.alias() + " ") + foreign.project() + ": request is not under "
                + GitRemote.projectPath(foreign.remote());
    }

    private static String host(String taskId, String project) {
        return taskId + "/" + project;
    }
}
