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
import dev.jagt.orchestrator.flow.Generation;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.stop.TaskStop;
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
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {}, gap -> {});

  private final Generation readAt = new Generation(Generation.Kind.HEAD, "");
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
        "",
        readAt);

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
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).checks().checksUnread())
        .containsExactly(new ChecksUnread(3_000, "list_pipelines: 403 Forbidden"));
  }

  @Test
  void keepsAnEarlierRoundsVerdictFlaggedWhenThisSweepCouldNotReadTheChecks() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(failing));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(openRequest, new ReviewFacts.Pipeline("unknown", "", ""), noDiscussion),
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    TaskState after = stamped.getValue().apply(reducer, failing);
    assertThat(after.checks().pipelineStatus()).isEqualTo("failed");
    assertThat(after.checks().unread()).isTrue();
  }

  @Test
  void stampsWhetherTheRoundIsApprovedSoBothSurfacesCanShowItBesideTheRequest() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(approvedRequest, new ReviewFacts.Pipeline("success", "", ""), noDiscussion),
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).round().approved()).isTrue();
  }

  @Test
  void keepsWhatTheHostSaidAboutTheChecksOnTheTask() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(openRequest, new ReviewFacts.Pipeline("SUCCEEDED", "", ""), noDiscussion),
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).checks().pipelineStatus())
        .isEqualTo("SUCCEEDED");
  }

  @Test
  void keepsWhenTheHostSaysTheRequestWasOpened() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(polling));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 1_700_000_000_000L),
            new ReviewFacts.Pipeline("running", "", ""),
            noDiscussion),
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).round().requestOpenedAt())
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
        new ReviewFacts(openRequest, new ReviewFacts.Pipeline("running", "", ""), noDiscussion),
        "",
        readAt);

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
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).round().requestOpenedAt())
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
            "",
            readAt);

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

    RoundRecord.Recorded answer = roundRecord.record("ABC-1", "shop", facts, "", readAt);

    assertThat(answer.red()).isEqualTo(infrastructure);
  }

  @Test
  void writesOnlyTheChecksOfReadThatListedThemWithoutJudgingOrStopping() {
    when(stateService.task("ABC-1")).thenReturn(Optional.of(failing));

    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(approvedRequest, new ReviewFacts.Pipeline("success", "", ""), noDiscussion),
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    TaskState after = stamped.getValue().apply(reducer, failing);
    assertThat(after.checks().pipelineStatus()).isEqualTo("success");
    assertThat(after.round().approved()).isTrue();
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
        "",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, polling).checks().pipelineUrl())
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
        "",
        readAt);

    verify(stateService, never()).reduce(any(), any());
  }

  @Test
  void writesNothingForReadThatListedNoChecks() {
    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(openRequest, new ReviewFacts.Pipeline("unknown", "", ""), noDiscussion),
        "",
        readAt);

    verifyNoInteractions(stateService, stops, causes);
  }

  @Test
  void dropsTheChecksTheRoundCouldNotListWhereTheHeadMovedSinceTheReadBegan() {
    TaskState moved = polling.withHead("tip-2");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(moved));

    roundRecord.record(
        "ABC-1",
        "shop",
        new ReviewFacts(openRequest, new ReviewFacts.Pipeline("unknown", "", ""), noDiscussion),
        "tip-1",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, moved)).isEqualTo(moved);
  }

  @Test
  void dropsTheChecksTheRoundListedWhereTheHeadMovedSinceTheReadBegan() {
    TaskState moved = failing.withHead("tip-2");
    when(stateService.task("ABC-1")).thenReturn(Optional.of(moved));

    roundRecord.recordListed(
        "ABC-1",
        new ReviewFacts(approvedRequest, new ReviewFacts.Pipeline("success", "", ""), noDiscussion),
        "tip-1",
        readAt);

    verify(stateService).reduce(eq("ABC-1"), stamped.capture());
    assertThat(stamped.getValue().apply(reducer, moved)).isEqualTo(moved);
  }
}
