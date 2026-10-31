package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.Master;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoundsTest {

    private final MasterReview masterReview = mock(MasterReview.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final Rounds rounds = new Rounds(configService, masterReview);

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

    @Test
    void theMasterNoLongerReadsAHandBackItJudgedBeforeTheSealWasKeptWithItsRound() {
        String json = """
                {"status":"REVIEW_PENDING","lastActiveTimestamp":5,"mrCreatedAt":0,"lastPolledAt":0,
                 "repos":[{"project":"proj","worktreePath":"/wt"}],
                 "turn":{"step":"ship","id":3,"digest":"","since":4,"marks":{"master-review:seal":"fingerprint"}}}
                """;
        TaskState task = new JsonMapper().readValue(json, TaskState.class);
        when(masterReview.readsTheRoundInFront(task)).thenReturn(true);

        var round = rounds.of(task, ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        assertThat(round.masterReading()).isFalse();
    }

    @Test
    void theMasterReadsTheHandBackAgainWhereTheVerdictFileIsGone() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
                .master(Master.defaults().withSeal("fingerprint", 0)).build();
        when(masterReview.readsTheRoundInFront(task)).thenReturn(false);

        var round = rounds.of(task, ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

        assertThat(round.masterReading()).isTrue();
    }

    @Test
    void theMasterHasJudgedNothingWhileItIsOff() {
        TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
        when(masterReview.readsTheRoundInFront(task)).thenReturn(true);
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

        assertThat(rounds.judged(task)).isFalse();
    }
}
