package dev.jagt.orchestrator.job;

import static dev.jagt.orchestrator.service.TestStateServiceBuilder.stateService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskMovedOn;
import dev.jagt.orchestrator.service.master.MasterPanel;
import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterReviewJobTest {

  private final ConfigService config = mock(ConfigService.class);
  private final MasterPanel panel = mock(MasterPanel.class);
  private final MasterRuling verdicts = mock(MasterRuling.class);
  private final MasterReview.Read acceptanceReady =
      new MasterReview.Read("acceptance", new Judgement("", "ready", List.of(), "", List.of(), ""));
  @TempDir Path root;
  private StateService state;
  private MasterReview reviews;
  private MasterReviewJob job;

  @BeforeEach
  void masterReviewJobOverStateOnDisk() {
    state = stateService().withRoot(root).build();
    reviews = new MasterReview(state);
    job = new MasterReviewJob(state, config, panel, reviews, verdicts);
  }

  @Test
  void readsNoRoundWhileTheMasterIsOff() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("off", null, null, null, null)));

    job.run();

    verify(panel, never()).review(anyString(), any(), any());
  }

  @Test
  void handsRoundThatCameBackAndNobodyJudgedToTheMastersPanel() {
    TaskState back =
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(2_000L)
            .build();
    state.putTask("ABC-1", back);
    ConfigService.ConfigFile.MasterConfig master =
        new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(master));

    job.run();

    verify(panel).review("ABC-1", back, master);
  }

  @Test
  void handsPlanToThePlanReadingPanelNotTheRoundReadingOne() {
    TaskState planned = TaskState.builder("proj", root.toString(), TaskStatus.PLAN_PENDING).build();
    state.putTask("ABC-1", planned);
    ConfigService.ConfigFile.MasterConfig master =
        new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null);
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(master));

    job.run();

    verify(panel).plan("ABC-1", planned, master);
    verify(panel, never()).review(anyString(), any(), any());
  }

  @Test
  void readsNothingOfTaskTheSessionStillWorksOn() {
    state.putTask(
        "ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    job.run();

    verify(panel, never()).review(anyString(), any(), any());
    verify(panel, never()).plan(anyString(), any(), any());
  }

  @Nested
  class WhereTheRoundCameBackNotReady {

    private TaskState back;

    @BeforeEach
    void theRoundIsOnTheBoard() {
      back =
          TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
              .build()
              .withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()));
      state.putTask("ABC-1", back);
    }

    @Test
    void actsOnTheVerdictWrittenAboutTheRoundInsteadOfAskingAgain() {
      reviews.write(
          "ABC-1", back, new MasterReview.Review(5, "", "", "", List.of(acceptanceReady)), "");
      ConfigService.ConfigFile.MasterConfig master =
          new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
      when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(master));
      TaskState written = state.task("ABC-1").orElseThrow();

      job.run();

      verify(verdicts).act(eq("ABC-1"), eq(written), any(MasterReview.Verdict.class), eq(master));
      verify(panel, never()).review(anyString(), any(), any());
    }

    @Test
    void keepsTheTickGoingWhenVerdictFindsTheTaskMovedOn() {
      when(config.load())
          .thenReturn(
              ConfigService.ConfigFile.defaults()
                  .withMaster(
                      new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));
      when(verdicts.act(anyString(), any(), any(), any())).thenThrow(new TaskMovedOn("ABC-1"));
      reviews.write(
          "ABC-1", back, new MasterReview.Review(5, "", "", "", List.of(acceptanceReady)), "");
      TaskState written = state.task("ABC-1").orElseThrow();

      job.run();

      verify(verdicts).act(eq("ABC-1"), eq(written), any(MasterReview.Verdict.class), any());
    }

    @Test
    void asksAgainWhenTheOnlyVerdictWasAskedAboutAnEarlierHandBack() {
      reviews.write(
          "ABC-1", back, new MasterReview.Review(3, "", "", "", List.of(acceptanceReady)), "");
      ConfigService.ConfigFile.MasterConfig master =
          new ConfigService.ConfigFile.MasterConfig("act", null, null, null, null);
      when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withMaster(master));

      job.run();

      verify(verdicts, never()).act(anyString(), any(), any(), any());
      verify(panel).review("ABC-1", state.task("ABC-1").orElseThrow(), master);
    }
  }

  @Test
  void leavesRoundWhoseQuestionTheMasterAnswersToTheAnswerNotTheReview() {
    TaskState asking =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .message("override the gate?")
            .report(AgentReport.QUESTION)
            .build();

    boolean reviewable = MasterReviewJob.reviewable(asking, false, true);

    assertThat(reviewable).isFalse();
  }

  @Test
  void handsPlanNobodyReadYetToTheMaster() {
    TaskState planned =
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING).message("plan written").build();

    boolean reviewable = MasterReviewJob.reviewable(planned, false, true);

    assertThat(reviewable).isTrue();
  }

  @Test
  void readsPlanWhoseHandBackCarriesQuestionTheMasterAnswers() {
    TaskState planned =
        TaskState.builder("proj", "/wt", TaskStatus.PLAN_PENDING)
            .message("plan revised, approve?")
            .report(AgentReport.QUESTION)
            .build();

    boolean reviewable = MasterReviewJob.reviewable(planned, false, true);

    assertThat(reviewable).isTrue();
  }

  @Test
  void settlesTheMastersSealAgainstTheVerdictFileOnEveryTick() throws Exception {
    TaskState sealed =
        TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING)
            .lastActiveTimestamp(2_000L)
            .build()
            .withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()));
    state.putTask("ABC-1", sealed);
    reviews.write("ABC-1", sealed, new MasterReview.Review(5, "", "", "", List.of()), "");
    Files.delete(root.resolve(".jagt").resolve("master-review.json"));
    when(config.load())
        .thenReturn(
            ConfigService.ConfigFile.defaults()
                .withMaster(
                    new ConfigService.ConfigFile.MasterConfig("judge", null, null, null, null)));

    job.run();

    assertThat(MasterPhase.of(state.task("ABC-1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }
}
