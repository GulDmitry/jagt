package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Arbiter;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.MasterPhase;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.Turns;
import dev.jagt.orchestrator.port.RequestlessShip;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a task's last round left behind. */
@Component
@RequiredArgsConstructor
public class Rounds implements PhaseProfiles {

    private final ConfigService configService;
    private final RequestlessShip requestless;

    public RoundState of(TaskState task) {
        return of(task, configService.load());
    }

    @Override
    public Arbiter.Profile at(TaskState task) {
        return of(task).master();
    }

    /** The Master runs and has read the plan or round in front. */
    public boolean judged(TaskState task) {
        return configService.load().master().running() && MasterPhase.readTheRoundInFront(task);
    }

    /** Where the Master read the round in front or left the task to the human, nothing of it stands in any more. */
    public RoundState of(TaskState task, ConfigService.ConfigFile config) {
        Arbiter.Profile master = config.master().profile().at(MasterPhase.of(task));
        boolean drafted = ReviewDrafts.pending(task, task.status(), config.codeReview().shipPostsEveryDraft());
        return RoundState.of(task.report(), drafted)
                .withRepliesWrittenAt(drafted ? ReviewDrafts.writtenAt(task) : 0)
                .withMaster(master)
                .withMasterReading(FlowRules.readByTheMaster(task.status()) && master.running())
                .withCheckers(Checkers.of(task))
                .withRequestless(requestless.stands(task));
    }
}
