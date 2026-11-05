package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopHold;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.service.master.MasterStopAnswer;
import dev.jagt.orchestrator.service.master.MasterStopPanel;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** In act, a stop jagt set for a loop it suspects is asked of the Master once; what it does not lift stays the human's. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterStopJob implements TaskJob {

    private final ConfigService configService;
    private final MasterStopPanel panel;
    private final MasterStopMoves moves;
    private final TaskTurns turns;

    @Override
    public String id() {
        return "master-stop";
    }

    @Override
    public Serves serves() {
        return Serves.MASTER;
    }

    @Override
    public String describe() {
        return "ask the Master once about a stop for a suspected loop, and lift it where it says so";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(5);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStops(StopKind.LIFT);
    }

    @Override
    public void run(String taskId, TaskState task) {
        ConfigService.ConfigFile.MasterConfig config = configService.load().master();
        if (StopHold.of(task, config.profile()) != StopHold.MASTER) {
            return;
        }
        TaskStop stop = Objects.requireNonNull(task.stop());
        turns.askedStop(taskId, stop.key());
        Optional<MasterStopAnswer> answer = panel.ask(taskId, task, config).filter(said -> decides(task, said));
        if (answer.isEmpty()) {
            log.atInfo().setMessage("master leaves the stop").addKeyValue("task", taskId)
                    .addKeyValue("trigger", stop.trigger()).addKeyValue("effect", "the stop is the human's to lift").log();
            return;
        }
        log.atInfo().setMessage("master lifts the stop").addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger())
                .addKeyValue("verdict", answer.get().kind()).addKeyValue("reason", answer.get().text()).log();
        lift(taskId, answer.get());
    }

    /** A plan waiting is answered by its review, so a task there is not sent back to work. */
    private static boolean decides(TaskState task, MasterStopAnswer answer) {
        return switch (answer.kind()) {
            case LIFT -> true;
            case RETURN -> !FlowRules.holdsAPlan(task.status());
            case HUMAN -> false;
        };
    }

    private void lift(String taskId, MasterStopAnswer answer) {
        if (moves.lift(taskId) && answer.kind() == MasterStopAnswer.Kind.RETURN && !moves.returned(taskId, answer.text())) {
            log.atWarn().setMessage("master return not told").addKeyValue("task", taskId)
                    .addKeyValue("cause", "the telling was refused or the task moved on")
                    .addKeyValue("effect", "the stop is lifted, the session was not told").log();
        }
    }
}
