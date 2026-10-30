package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.TaskTurns;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Turn;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterRight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** In act, a request green with every thread closed is deployed as the human's own press would. */
@Service
@RequiredArgsConstructor
public class MasterDeployJob implements TaskJob {

    private final ConfigService configService;
    private final MasterPress press;
    private final TaskTurns turns;

    @Override
    public String id() {
        return "master-deploy";
    }

    @Override
    public Serves serves() {
        return Serves.MASTER;
    }

    @Override
    public String describe() {
        return "deploy each task whose request is green with every thread closed";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::deployedByTheMaster);
    }

    @Override
    public void run(String taskId, TaskState task) {
        Turns.Profile profile = configService.load().master().profile();
        String arrival = String.valueOf(task.statusSince());
        // Once per arrival, kept on the task: a refused deploy is not pressed again, nor after a restart.
        if (!profile.may(MasterRight.DEPLOY)
                || Turns.next(task.status(), task.report(), task.stopped(), profile) != Turn.Holder.MASTER
                || task.turnOrNone().marked(TaskTurns.PRESSED_DEPLOY, arrival)) {
            return;
        }
        turns.mark(taskId, TaskTurns.PRESSED_DEPLOY, arrival);
        press.press(taskId, task, TaskAction.DEPLOY);
    }
}
