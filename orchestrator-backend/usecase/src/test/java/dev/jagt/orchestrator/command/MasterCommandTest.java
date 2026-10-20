package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterCommandTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final MasterRecord record = mock(MasterRecord.class);
    private final UsageTracker usage = mock(UsageTracker.class);

    @Test
    void offersALessonForTheTaskYouRevertedAfterItPassed() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(1, 1, 0, 0, 1, 0));
        when(record.latestDisagreement()).thenReturn(Optional.of(new MasterRecord.Disagreement("ABC-42", true)));

        String report = new MasterCommand(configService, mock(UsageTracker.class), record).run("");

        assertThat(report).contains("\n  lesson:    ABC-42 passed, then you reverted it — to teach the Master, run"
                + " /master-lesson with its master-review.md, what you said and why");
    }

    @Test
    void saysHowToTurnItOnWhileTheMasterIsOff() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).isEqualTo("master: off. To turn it on, set `orchestrator.master.mode` to judge.");
    }

    @Test
    void saysAJudgingMasterReadsAndPressesNothing() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("master: judge — it reads and writes what it found; it presses nothing.");
    }

    @Test
    void saysAnActingMasterPressesTheButtonYouWould() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("master: act — it also presses the button you would have.");
    }

    @Test
    void namesTheModelTheMasterWasGiven() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, "opus", null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  model:     opus");
    }

    @Test
    void saysTheModelIsInheritedWhereNoneWasGiven() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  model:     inherited from the agent CLI");
    }

    @Test
    void listsTheStepsYouKeptForYourself() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of("ship", "deploy"), null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  yours:     ship, deploy");
    }

    @Test
    void saysYouKeptNothingBeyondTheStepsNoMasterHolds() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  yours:     nothing — every step but `sweep`, `revert` and `done`");
    }

    @Test
    void staysSilentAboutWhatItJudgedBeforeItJudgedAnything() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).doesNotContain("judged:");
    }

    @Test
    void saysYouDidTheSameWithEveryRoundItJudged() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(3, 2, 1, 0, 0, 0));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  judged:    3 finished, 2 passed, 1 asked you — and you did the same with every one");
    }

    @Test
    void countsHowYouDifferedFromWhatItJudged() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(5, 3, 0, 1, 1, 2));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  judged:    5 finished, 3 passed; you differed on 4 (1 passed and never deployed,"
                + " 1 passed and reverted, 2 failed and deployed)");
    }

    @Test
    void totalsWhatTheReviewCostSinceJagtStarted() {
        when(configService.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withMaster(new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
        when(record.seen()).thenReturn(new MasterRecord.Seen(0, 0, 0, 0, 0, 0));
        when(usage.sessionByKind()).thenReturn(Map.of(AssistantCallKind.MASTER_REVIEW,
                new TokenUsage(2, 1000, 0, 500, 1.25, 0, 0)));

        String report = new MasterCommand(configService, usage, record).run("");

        assertThat(report).contains("\n  spent:     1.5k tokens, $1.25 since jagt started");
    }
}
