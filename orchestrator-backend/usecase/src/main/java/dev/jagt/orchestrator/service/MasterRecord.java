package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.task.FinishedTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What the reviewer's verdicts turned out to be worth, read off the finished record rather than off any
 * opinion of its own. A verdict AND what the task then did are both facts, and the only two ways they can
 * disagree are counted: it passed work that never landed, and it failed work you landed anyway.
 */
@Service
@RequiredArgsConstructor
public class MasterRecord {

    private final FinishedTasks finished;

    /** Zeroes where nothing has been judged yet, which reads the same as a reviewer nobody has turned on. */
    public record Seen(int judged, int passed, int passedNeverDeployed, int failedButDeployed) {

        public boolean any() {
            return judged > 0;
        }

        public int disagreed() {
            return passedNeverDeployed + failedButDeployed;
        }
    }

    public Seen seen() {
        List<FinishedTask> judged = finished.all().stream().filter(FinishedTask::judged).toList();
        return new Seen(judged.size(),
                (int) judged.stream().filter(FinishedTask::passed).count(),
                (int) judged.stream().filter(task -> task.passed() && !task.reached(TaskStatus.DEPLOYED))
                        .count(),
                (int) judged.stream().filter(task -> !task.passed() && task.reached(TaskStatus.DEPLOYED))
                        .count());
    }
}
