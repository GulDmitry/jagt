package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoundRecordTest {

  private final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()));

  private final StateService stateService = mock(StateService.class);
  private final TaskStops stops = mock(TaskStops.class);
  private final RedCauses causes = mock(RedCauses.class);
  private final TaskState polling = TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).build();
  private final TaskState failing =
      TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING).pipelineStatus("failed").build();
  private final ReviewFacts.Request approvedRequest =
      new ReviewFacts.Request(true, true, false, false, 0);
  private final ReviewFacts.Request openRequest =
      new ReviewFacts.Request(true, false, false, false, 0);
  private final ReviewFacts.Discussion noDiscussion =
      new ReviewFacts.Discussion(List.of(), List.of(), List.of());
  private final ArgumentCaptor<BiFunction<TaskReducer, TaskState, TaskState>> stamped =
      ArgumentCaptor.captor();
  private final RoundRecord roundRecord =
      new RoundRecord(
          stateService, stops, Clock.fixed(Instant.ofEpochMilli(3_000), ZoneOffset.UTC), causes);

  @Test
  void stopsTheTaskWhenTheThirdReadRunningCouldNotListTheChecks() {
    ArgumentCaptor<Function<TaskState, Optional<TaskStop>>> tripped = ArgumentCaptor.captor();

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("unknown", "list_pipelines: 403 Forbidden", ""),
            noDiscussion),
        "");

    verify(stops).trip(eq("ABC-1"), tripped.capture());
    Optional<TaskStop> stop =
        tripped
            .getValue()
            .apply(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .build()
                    .withChecksUnread(new ChecksUnread(1_000, "list_pipelines: 403 Forbidden"))
                    .withChecksUnread(new ChecksUnread(2_000, "list_pipelines: 403 Forbidden"))
                    .withChecksUnread(new ChecksUnread(3_000, "list_pipelines: 403 Forbidden")));
    assertThat(stop).map(TaskStop::trigger).contains(TaskStop.Trigger.CHECKS);
  }

  @Test
  void keepsTheReadsOwnWordsForWhyItCouldNotListTheChecks() {

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("unknown", "list_pipelines: 403 Forbidden", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .checksUnread())
        .containsExactly(new ChecksUnread(3_000, "list_pipelines: 403 Forbidden"));
  }

  @Test
  void keepsAnEarlierRoundsVerdictFlaggedWhenThisSweepCouldNotReadTheChecks() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                failing));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("unknown", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    TaskState after =
        stamped
            .getValue()
            .apply(reducer,
                failing);
    assertThat(after.pipelineStatus()).isEqualTo("failed");
    assertThat(after.pipelineUnread()).isTrue();
  }

  @Test
  void stampsWhetherTheRoundIsApprovedSoBothSurfacesCanShowItBesideTheRequest() {
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            approvedRequest,
            new ReviewFacts.Pipeline("success", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .approved())
        .isTrue();
  }

  @Test
  void keepsWhatTheHostSaidAboutTheChecksOnTheTask() {
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("SUCCEEDED", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .pipelineStatus())
        .isEqualTo("SUCCEEDED");
  }

  @Test
  void keepsWhenTheHostSaysTheRequestWasOpened() {
    when(stateService.task("ABC-1"))
        .thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 1_700_000_000_000L),
            new ReviewFacts.Pipeline("running", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .requestOpenedAt())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  void leavesTheRequestsAgeAloneWhenTheReadCouldNotSayWhenItWasOpened() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .pipelineStatus("running")
                    .approved(false)
                    .requestOpenedAt(1_700_000_000_000L)
                    .build()));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("running", "", ""),
            noDiscussion),
        "");

    verify(stateService, never()).reduce(eq("ABC-1"), any());
  }

  @Test
  void keepsTheNewAgeOfTheRequestWhenNothingElseAboutTheRoundChanged() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .pipelineStatus("running")
                    .approved(false)
                    .requestOpenedAt(1_700_000_000_000L)
                    .build()));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 1_700_000_500_000L),
            new ReviewFacts.Pipeline("running", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .requestOpenedAt())
        .isEqualTo(1_700_000_500_000L);
  }

  @Test
  void answersWithTheStopTheTripLeftStandingOnTheTask() {
    TaskStop standing =
        new TaskStop(TaskStop.Trigger.CHECKS, 3_000, "3 reads running could not list the checks");
    when(stops.trip(eq("ABC-1"), any())).thenReturn(Optional.of(standing));

    RoundRecord.Recorded answer =
        roundRecord.record(
            "ABC-1",
            "shop",
            new ReviewFacts(
                openRequest,
                new ReviewFacts.Pipeline("unknown", "list_pipelines: 403 Forbidden", ""),
                noDiscussion),
            "");

    assertThat(answer.stop()).contains(standing);
  }

  @Test
  void answersWhoseFaultFailedRunIsWhereTheChecksWereRead() {
    ReviewFacts facts =
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("failed", "unit-tests", "https://host.invalid/p/1"),
            noDiscussion);
    RedCauses.Verdict infrastructure =
        new RedCauses.Verdict(RedCause.INFRASTRUCTURE, true, "waiting", Optional.empty());
    when(causes.judge("ABC-1", "shop", facts, Pipeline.RED)).thenReturn(infrastructure);

    RoundRecord.Recorded answer = roundRecord.record("ABC-1", "shop", facts, "");

    assertThat(answer.red()).isEqualTo(infrastructure);
  }

  @Test
  void writesOnlyTheChecksOfReadThatListedThemWithoutJudgingOrStopping() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                failing));

    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(
            approvedRequest,
            new ReviewFacts.Pipeline("success", "", ""),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    TaskState after =
        stamped
            .getValue()
            .apply(reducer,
                failing);
    assertThat(after.pipelineStatus()).isEqualTo("success");
    assertThat(after.approved()).isTrue();
    verifyNoInteractions(stops, causes);
  }

  @Test
  void writesThePipelineLinkWhenOnlyItIsNew() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .pipelineStatus("success")
                    .build()));

    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("success", "", "https://host.invalid/p/9"),
            noDiscussion),
        "");

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(
            stamped
                .getValue()
                .apply(reducer, polling)
                .pipelineUrl())
        .isEqualTo("https://host.invalid/p/9");
  }

  @Test
  void writesNothingWhenTheReadRepeatsWhatTheTaskHolds() {
    when(stateService.task("ABC-1"))
        .thenReturn(
            Optional.of(
                TaskState.builder("proj", "/wt", TaskStatus.CI_POLLING)
                    .pipelineStatus("success")
                    .pipelineUrl("https://host.invalid/p/9")
                    .approved(false)
                    .build()));

    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("success", "", "https://host.invalid/p/9"),
            noDiscussion),
        "");

    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void writesNothingForReadThatListedNoChecks() {
    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(
            openRequest,
            new ReviewFacts.Pipeline("unknown", "", ""),
            noDiscussion),
        "");

    verifyNoInteractions(stateService, stops, causes);
  }
}
