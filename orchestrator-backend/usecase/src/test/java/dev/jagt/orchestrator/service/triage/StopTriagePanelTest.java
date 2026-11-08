package dev.jagt.orchestrator.service.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.master.ChargedReviews;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.RoundTrail;
import dev.jagt.orchestrator.task.StatusChange;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class StopTriagePanelTest {

  private final ChargedReviews reviews = mock(ChargedReviews.class);
  private final TaskState stopped =
      TaskState.builder("proj", "/wt/alpha", TaskStatus.IN_PROGRESS)
          .build()
          .withStop(new TaskStop(TaskStop.Trigger.ROUNDS, 0L, "round sent 4 times"));
  private final StopTriagePanel.Trail trail =
      new StopTriagePanel.Trail("10:00 round relayed", "abc1234 Change");

  private StopTriagePanel panel(Path root, String brief) throws Exception {
    Files.createDirectories(root.resolve("roles/triage"));
    if (brief != null) {
      Files.writeString(root.resolve("roles/triage/brief.md.dist"), brief);
    }
    return new StopTriagePanel(
        reviews,
        new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())));
  }

  private static Judgement answer(String verdict, String issue, String pattern) {
    return new Judgement(
        "",
        verdict,
        List.of(new Finding("-", issue, pattern, "noise", "-", "scope")),
        "",
        List.of(new RoundReviewer.Premise("the trail shows one tree", "")),
        "");
  }

  @Test
  void aReadyVerdictIsALiftSafeAndTheFindingNamesWhoClassAndFamily(@TempDir Path root)
      throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(answer("ready", "jagt: ships counted past a merge", "H"));

    StopTriage read = panel(root, "brief").read("ABC-42", stopped, trail, "haiku");

    assertThat(read)
        .isEqualTo(
            new StopTriage(
                StopTriage.Who.JAGT,
                "ships counted past a merge",
                "H",
                StopTriage.Lift.SAFE,
                "the trail shows one tree",
                ""));
  }

  @Test
  void aNotReadyVerdictIsALiftUnsafe(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(answer("not ready", "session: same tree handed back", "F"));

    StopTriage read = panel(root, "brief").read("ABC-42", stopped, trail, "haiku");

    assertThat(read.lift()).isEqualTo(StopTriage.Lift.UNSAFE);
    assertThat(read.who()).isEqualTo(StopTriage.Who.SESSION);
  }

  @Test
  void aFamilyOutsideAToMIsUnknown(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(answer("ready", "none: the work moved", "Z"));

    StopTriage read = panel(root, "brief").read("ABC-42", stopped, trail, "haiku");

    assertThat(read.family()).isEqualTo("unknown");
  }

  @Test
  void theReaderIsGivenTheBriefTheStopTheTrailAndTheCommits(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(answer("ready", "none: the work moved", "unknown"));

    panel(root, "the triage brief").read("ABC-42", stopped, trail, "haiku");

    ArgumentCaptor<RoundReviewer.Round> round = ArgumentCaptor.forClass(RoundReviewer.Round.class);
    verify(reviews).review(eq("ABC-42"), round.capture());
    assertThat(round.getValue().prompt())
        .contains("the triage brief", "round sent 4 times", "10:00 round relayed", "abc1234 Change");
    assertThat(round.getValue().model()).isEqualTo("haiku");
    assertThat(round.getValue().worktrees()).containsExactly(Path.of("/wt/alpha"));
  }

  @Test
  void theEvidenceJoinsEveryPremiseTheReadRestedOn(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(
            new Judgement(
                "",
                "ready",
                List.of(new Finding("-", "none: the work moved", "unknown", "noise", "-", "scope")),
                "",
                List.of(
                    new RoundReviewer.Premise("tree changed", ""),
                    new RoundReviewer.Premise("tests green", "")),
                ""));

    StopTriage read = panel(root, "brief").read("ABC-42", stopped, trail, "haiku");

    assertThat(read.evidence()).isEqualTo("tree changed; tests green");
  }

  @Test
  void aFailedReadFailsNamingItsCause(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any())).thenReturn(Judgement.failed("git state changed"));

    StopTriagePanel panel = panel(root, "brief");

    assertThatThrownBy(() -> panel.read("ABC-42", stopped, trail, "haiku"))
        .isInstanceOf(StopTriageFailed.class)
        .hasMessage("git state changed");
  }

  @Test
  void aFindingNamingNoWhoFails(@TempDir Path root) throws Exception {
    when(reviews.review(eq("ABC-42"), any()))
        .thenReturn(answer("ready", "a loop of some kind", "F"));

    StopTriagePanel panel = panel(root, "brief");

    assertThatThrownBy(() -> panel.read("ABC-42", stopped, trail, "haiku"))
        .isInstanceOf(StopTriageFailed.class);
  }

  @Test
  void aMissingBriefFailsBeforeAnythingIsAsked(@TempDir Path root) throws Exception {
    StopTriagePanel panel = panel(root, null);

    assertThatThrownBy(() -> panel.read("ABC-42", stopped, trail, "haiku"))
        .isInstanceOf(StopTriageFailed.class);
  }

  @Test
  void theTrailListsTheLastTwelveStepsAndEveryRoundAsTheTaskRecordedThem() {
    List<StatusChange> steps =
        new ArrayList<>(
            IntStream.range(0, 13)
                .mapToObj(
                    index ->
                        new StatusChange(
                            TaskStatus.IN_PROGRESS, 1_000_000L * index, ActionOrigin.BOARD))
                .toList());
    steps.set(
        12,
        new StatusChange(TaskStatus.VERIFYING, 90_000_000L, null).because("probes red"));
    TaskState task =
        TaskState.builder("proj", "/wt/alpha", TaskStatus.IN_PROGRESS)
            .history(steps)
            .trail(
                new RoundTrail(
                    List.of(
                        new RoundTrail.Round(100_000_000L, 2, "unit-tests", "d", "n"),
                        new RoundTrail.Round(200_000_000L, 0, "", "e", "n")),
                    0))
            .build();

    StopTriagePanel.Trail read = StopTriagePanel.Trail.of(task);

    assertThat(read.excerpt().lines().toList())
        .hasSize(14)
        .doesNotContain("1970-01-01T00:00:00Z IN_PROGRESS by BOARD")
        .contains(
            "1970-01-01T00:16:40Z IN_PROGRESS by BOARD",
            "1970-01-02T01:00:00Z VERIFYING: probes red",
            "1970-01-02T03:46:40Z round, threads 2, failing unit-tests",
            "1970-01-03T07:33:20Z round, threads 0");
    assertThat(read.commits()).contains("/wt/alpha").contains("base");
  }
}
