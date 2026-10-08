package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundsTest {

    private final MasterReview masterReview = mock(MasterReview.class);
    private final Rounds rounds = new Rounds(mock(ConfigService.class), masterReview);

    @Test
    void theMasterReadsAHandBackItHasNotReadYetWhileItRuns() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        var round = rounds.of(task, ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        assertThat(round.masterReading()).isTrue();
    }

    @Test
    void nobodyReadsAHandBackBeforeTheHumanWhileTheMasterIsOff() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

        var round = rounds.of(task, ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        assertThat(round.masterReading()).isFalse();
    }

    @Test
    void theMasterNoLongerReadsAHandBackItHasAlreadyJudged() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(masterReview.readsTheRoundInFront(task)).thenReturn(true);

        var round = rounds.of(task, ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        assertThat(round.masterReading()).isFalse();
    }
}
