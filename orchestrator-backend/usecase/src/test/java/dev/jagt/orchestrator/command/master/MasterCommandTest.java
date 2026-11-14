package dev.jagt.orchestrator.command.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.SessionBrief;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.master.MasterBriefs;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterCommandTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final MasterRecord record = mock(MasterRecord.class);
  private final UsageTracker usage = mock(UsageTracker.class);
  private final OrchestratorPaths paths =
      new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/root"));
  private final MasterBriefs briefs = new MasterBriefs(paths, new SessionBrief(paths));
  private ConfigService.ConfigFile.MasterConfig master =
      new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
  private MasterRecord.Seen seen = new MasterRecord.Seen(0, 0, 0, 0, 0, 0);
  private MasterCommand command;

  @BeforeEach
  void masterJudgesAndHasSeenNothing() {
    when(configService.load())
        .thenAnswer(call -> ConfigService.ConfigFile.defaults().withMaster(master));
    when(record.seen()).thenAnswer(call -> seen);
    command = new MasterCommand(configService, usage, record, briefs);
  }

  @Test
  void offersLessonForTheTaskYouRevertedAfterItPassed() {
    seen = new MasterRecord.Seen(1, 1, 0, 0, 1, 0);
    when(record.latestDisagreement())
        .thenReturn(Optional.of(new MasterRecord.Disagreement("ABC-42", true)));

    String report = command.run("");

    assertThat(report)
        .contains(
            "\n  lesson:    ABC-42 passed, then you reverted it — to teach the Master, run"
                + " /master-lesson with its master-review.md, what you said and why");
  }

  @Test
  void namesTheBriefItJudgesBy() {

    String report = command.run("");

    assertThat(report).contains("judges by: /root/roles/master/brief.md");
  }

  @Test
  void saysHowToTurnItOnWhileTheMasterIsOff() {
    master = new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null);

    String report = command.run("");

    assertThat(report)
        .isEqualTo("master: off. To turn it on, set `orchestrator.roles.master.mode` to judge.");
  }

  @Test
  void saysJudgingMasterReadsAndPressesNothing() {

    String report = command.run("");

    assertThat(report)
        .contains("master: judge — it reads and writes what it found; it presses nothing.");
  }

  @Test
  void saysAnActingMasterPressesTheButtonYouWould() {
    master = new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);

    String report = command.run("");

    assertThat(report).contains("master: act — it also presses the button you would have.");
  }

  @Test
  void namesTheModelTheMasterWasGiven() {
    master = new ConfigService.ConfigFile.MasterConfig("judge", null, "opus", null, null);

    String report = command.run("");

    assertThat(report).contains("\n  model:     opus");
  }

  @Test
  void saysTheModelIsInheritedWhereNoneWasGiven() {

    String report = command.run("");

    assertThat(report).contains("\n  model:     inherited from the agent CLI");
  }

  @Test
  void listsTheStepsYouKeptForYourself() {
    master =
        new ConfigService.ConfigFile.MasterConfig(
            "act", null, null, List.of("ship", "deploy"), null);

    String report = command.run("");

    assertThat(report).contains("\n  yours:     ship, deploy");
  }

  @Test
  void saysYouKeptNothingBeyondTheStepsNoMasterHolds() {
    master = new ConfigService.ConfigFile.MasterConfig("act", null, null, List.of(), null);

    String report = command.run("");

    assertThat(report)
        .contains("\n  yours:     nothing — every step but `sweep`, `revert` and `done`");
  }

  @Test
  void staysSilentAboutWhatItJudgedBeforeItJudgedAnything() {

    String report = command.run("");

    assertThat(report).doesNotContain("judged:");
  }

  @Test
  void saysYouDidTheSameWithEveryRoundItJudged() {
    seen = new MasterRecord.Seen(3, 2, 1, 0, 0, 0);

    String report = command.run("");

    assertThat(report)
        .contains(
            "\n"
                + "  judged:    3 finished, 2 passed, 1 asked you — and you did the same with"
                + " every one");
  }

  @Test
  void countsHowYouDifferedFromWhatItJudged() {
    seen = new MasterRecord.Seen(5, 3, 0, 1, 1, 2);

    String report = command.run("");

    assertThat(report)
        .contains(
            "\n  judged:    5 finished, 3 passed; you differed on 4 (1 passed and never deployed,"
                + " 1 passed and reverted, 2 failed and deployed)");
  }

  @Test
  void totalsWhatTheReviewCostSinceJagtStarted() {
    when(usage.sessionByKind())
        .thenReturn(Map.of(AssistantCallKind.MASTER_REVIEW, new TokenUsage(2, 1000, 0, 500, 1.25)));

    String report = command.run("");

    assertThat(report).contains("\n  spent:     1.5k tokens, $1.25 since jagt started");
  }

  @Test
  void showsWhatTheReviewCostAsLowerBoundWhenAnUnpricedModelAnswered() {
    when(usage.sessionByKind())
        .thenReturn(
            Map.of(
                AssistantCallKind.MASTER_REVIEW,
                TokenUsage.ofCall(1000, 0, 500, 1.25).plus(TokenUsage.ofUnpricedCall(10, 0, 10))));

    String report = command.run("");

    assertThat(report).contains("tokens, ≥ $1.25 since jagt started");
  }
}
