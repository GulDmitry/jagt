package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.MasterPhase;
import dev.jagt.orchestrator.flow.QaPhase;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a task's last round left behind. */
@Component
@RequiredArgsConstructor
public class Rounds {

    private final ConfigService configService;
    private final MasterReview masterReview;

    public RoundState of(TaskState task) {
        return of(task, configService.load());
    }

    /** The Master runs and has read the plan or round in front. */
    public boolean judged(TaskState task) {
        return FlowRules.readByTheMaster(task.status()) && configService.load().master().running()
                && masterReview.readsTheRoundInFront(task);
    }

    /** Where the Master read the round in front or left the task to the human, nothing of it stands in any more. */
    public RoundState of(TaskState task, ConfigService.ConfigFile config) {
        Turns.Profile master = MasterPhase.of(task) == MasterPhase.STANDS_IN ? config.master().profile()
                : Turns.Profile.OFF;
        return RoundState.of(task.report(),
                        ReviewDrafts.pending(task, task.status(), config.codeReview().shipPostsEveryDraft()))
                .withMaster(master)
                .withMasterReading(FlowRules.readByTheMaster(task.status()) && master.running())
                .withQa(QaPhase.of(task));
    }
}
