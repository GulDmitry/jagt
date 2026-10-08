package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a task's last round left behind, one answer for the board and the ping alike. */
@Component
@RequiredArgsConstructor
public class Rounds {

    private final ConfigService configService;
    private final MasterReview masterReview;

    public RoundState of(TaskState task) {
        return of(task, configService.load());
    }

    public RoundState of(TaskState task, ConfigService.ConfigFile config) {
        return RoundState.of(task.message(),
                        ReviewDrafts.pending(task, task.status(), config.codeReview().shipPostsEveryDraft()))
                .withMasterReading(Move.masterReads(task.status()) && config.master().running()
                        && !masterReview.readsTheRoundInFront(task));
    }
}
