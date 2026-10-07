package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.FinishedTasks;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the reviewer's verdicts turned out to be worth, read off the finished record rather than off any
 * opinion of its own. A verdict AND what the task then did are both facts, and the only ways they can
 * disagree are counted: it passed work that never landed or that you took back out, and it failed work you
 * landed anyway. The record keeps the verdict's word but not when it was written, so a round that followed it
 * cannot be told from one that preceded it and is not counted.
 */
@Service
@RequiredArgsConstructor
public class MasterRecord {

    private final FinishedTasks finished;
    private final ConfigService configService;

    /** Zeroes where nothing has been judged yet, which reads the same as a reviewer nobody has turned on. */
    public record Seen(int judged, int passed, int asked, int passedNeverDeployed, int passedReverted,
                       int failedButDeployed) {

        public boolean any() {
            return judged > 0;
        }

        public int disagreed() {
            return passedNeverDeployed + passedReverted + failedButDeployed;
        }
    }

    public Seen seen() {
        List<FinishedTask> judged = finished.all().stream().filter(FinishedTask::judged).toList();
        return new Seen(judged.size(),
                count(judged, FinishedTask::passed),
                count(judged, FinishedTask::asked),
                count(judged, task -> task.passed() && task.rounds() > 0 && !task.reached(TaskStatus.DEPLOYED)
                        && deployable(task)),
                count(judged, task -> task.passed() && task.reached(TaskStatus.REVERTED)),
                count(judged, task -> !task.passed() && !task.asked() && task.judgedBefore(TaskStatus.DEPLOYED)));
    }

    /** A project jagt does not deploy, or no longer knows, owes no deploy: its work lands elsewhere. */
    private boolean deployable(FinishedTask task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        return task.projects().stream().map(projects::get)
                .allMatch(project -> project != null && project.deployBranch() != null
                        && !project.deployBranch().isBlank());
    }

    private static int count(List<FinishedTask> tasks, Predicate<FinishedTask> which) {
        return (int) tasks.stream().filter(which).count();
    }
}
