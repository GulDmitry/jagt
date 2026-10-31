package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.HumanWaits;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.task.HumanWait;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn.Holder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps on each task the stretches its turn rested with the human. An open one stays in {@code state.json}, so a
 * restart finds it open and the first sweep closes it only where the turn has left the human since.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HumanWaitRecorder {

    private final StateService stateService;
    private final Rounds rounds;
    private final Clock clock;

    @PostConstruct
    void follow() {
        sweep(stateService.tasks());
        stateService.onChange(written -> sweep(written.tasks()));
    }

    void sweep(Map<String, TaskState> tasks) {
        tasks.forEach((id, task) -> {
            if (HumanWaits.noted(task, holder(task), rounds.judged(task), clock.millis()) != task) {
                note(id);
            }
        });
    }

    private void note(String id) {
        AtomicReference<Optional<HumanWait>> ended = new AtomicReference<>(Optional.empty());
        stateService.updateTask(id, task -> {
            TaskState noted = HumanWaits.noted(task, holder(task), rounds.judged(task), clock.millis());
            ended.set(HumanWaits.ended(task, noted));
            return noted;
        });
        ended.get().ifPresent(wait -> log.atInfo().setMessage("human wait closed").addKeyValue("task", id)
                .addKeyValue("why", wait.why()).addKeyValue("since", wait.since()).addKeyValue("until", wait.until())
                .addKeyValue("millis", wait.until() - wait.since()).log());
    }

    private Holder holder(TaskState task) {
        return Turns.next(task.status(), task.report(), task.stopped(), task.qaReading(), rounds.of(task).master());
    }
}
