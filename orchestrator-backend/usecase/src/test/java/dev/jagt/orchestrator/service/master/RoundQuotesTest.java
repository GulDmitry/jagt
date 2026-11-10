package dev.jagt.orchestrator.service.master;

import static dev.jagt.orchestrator.service.master.RoundQuotesFixture.ready;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.MasterPhase;
import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.AgentReport;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundQuotesTest {

  private final RoundFacts facts = mock(RoundFacts.class);
  private final MasterReview reviews = new MasterReview(mock(StateService.class));
  private final RoundQuotes quotes = new RoundQuotes(facts, mock(MasterDecisions.class), reviews);

  @Test
  void keepsReadyVerdictWhenOnlyTheBaseMovedUnderTheTasksOwnDiff(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task = fixture.store(fixture.pending(), ready("abc123", "PLAIN", "in1"));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = fixture.quotes.carried("a1", task, "in1");

    assertThat(carried).isTrue();
    task = fixture.state.task("a1").orElseThrow();
    assertThat(MasterPhase.of(task)).isEqualTo(MasterPhase.READ);
  }

  @Test
  void readsTheRoundAnewWhenTheTasksOwnDiffChanged(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task = fixture.store(fixture.pending(), ready("abc123", "PLAIN", "in1"));
    when(facts.ownDiff(any())).thenReturn(Optional.of("def456"));

    boolean carried = fixture.quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
    assertThat(MasterPhase.of(task)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void keepsNotReadyVerdictWhenTheSessionHandedBackTheSameOwnDiffAndKind(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task =
        fixture.store(
            TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1")
                .report(AgentReport.NO_CHANGES)
                .lastActiveTimestamp(5_000)
                .build(),
            new MasterReview.Review(
                1_000,
                "abc123",
                "NO_CHANGES",
                "in1",
                List.of(
                    new MasterReview.Read(
                        "acceptance",
                        new Judgement(
                            "",
                            "not ready",
                            List.of(
                                new Finding("Foo.java", "drops v2", "", "wrong", "", "missing")),
                            "",
                            List.of(),
                            "")))));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = fixture.quotes.carried("a1", task, "in1");

    assertThat(carried).isTrue();
    task = fixture.state.task("a1").orElseThrow();
    assertThat(fixture.reviews.of(task).orElseThrow().kind())
        .isEqualTo(MasterReview.Kind.NOT_READY);
  }

  @Test
  void readsTheRoundAnewWhenTheHandBackIsOfAnotherKind(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task =
        fixture.store(
            TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .alias("a1")
                .report(AgentReport.NO_CHANGES)
                .lastActiveTimestamp(5_000)
                .build(),
            ready("abc123", "PLAIN", "in1"));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = fixture.quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhereTheVerdictWasStoredBeforeItKeptTheReportKind(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task = fixture.store(fixture.pending(), ready("abc123", null, "in1"));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = fixture.quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhenTheHumanSaidSomethingNewOverTheSameDiffAndKind(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task = fixture.pending();
    when(facts.ask(eq("ABC-1"), any())).thenReturn("Summary");
    when(facts.notes(any())).thenReturn("");
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of("keep v2")));
    String read = fixture.quotes.inputs("ABC-1", task).orElseThrow();
    task = fixture.store(task, ready("abc123", "PLAIN", read));
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of("keep v2", "drop v1")));

    boolean carried =
        fixture.quotes.carried("a1", task, fixture.quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhenTheSessionsNotesChangedOverTheSameDiffAndKind(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task = fixture.pending();
    when(facts.ask(eq("ABC-1"), any())).thenReturn("Summary");
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of()));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.notes(any())).thenReturn("");
    String read = fixture.quotes.inputs("ABC-1", task).orElseThrow();
    task = fixture.store(task, ready("abc123", "PLAIN", read));
    when(facts.notes(any())).thenReturn("disputed: the guard is right");

    boolean carried =
        fixture.quotes.carried("a1", task, fixture.quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @Test
  void keepsVerdictWhenRestartForgotTheTicketOverTheSameRound(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    when(facts.ticket("ABC-1")).thenReturn(Optional.of("Summary: accept v3 calls"));
    TaskState task = fixture.storeQuietRead(fixture.pending());
    when(facts.ticket("ABC-1")).thenReturn(Optional.empty());

    boolean carried =
        fixture.quotes.carried("a1", task, fixture.quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isTrue();
  }

  @Test
  void readsTheRoundAnewWhenTheTicketItReadChanged(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    when(facts.ticket("ABC-1")).thenReturn(Optional.of("accept v3 calls"));
    TaskState task = fixture.storeQuietRead(fixture.pending());
    when(facts.ticket("ABC-1")).thenReturn(Optional.of("accept v4 calls"));

    boolean carried =
        fixture.quotes.carried("a1", task, fixture.quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewOnceTheTicketIsReadWhereTheVerdictHadNone(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    when(facts.ticket("ABC-1")).thenReturn(Optional.empty());
    TaskState task = fixture.storeQuietRead(fixture.pending());
    when(facts.ticket("ABC-1")).thenReturn(Optional.of("accept v3 calls"));

    boolean carried =
        fixture.quotes.carried("a1", task, fixture.quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @Test
  void callsTheFirstReadOfRoundFirst(@TempDir Path worktree) {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    String trigger = quotes.trigger(task, "in1");

    assertThat(trigger).isEqualTo("first");
  }

  @Test
  void callsRereadOfTheSameInputsRetryAndChangedDiffChange(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task =
        fixture.store(
            fixture.pending(),
            new MasterReview.Review(1_000, "abc123", "PLAIN", "in1", List.of()));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    String sameInputs = fixture.quotes.trigger(task, "in1");
    when(facts.ownDiff(any())).thenReturn(Optional.of("def456"));

    String changedDiff = fixture.quotes.trigger(task, "in1");

    assertThat(sameInputs).isEqualTo("retry");
    assertThat(changedDiff).isEqualTo("changed");
  }

  @Test
  void fingerprintsRoundByItsOwnDiffAndInputs(@TempDir Path worktree) {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    String first = quotes.fingerprint(task, "in1");
    String again = quotes.fingerprint(task, "in1");
    when(facts.ownDiff(any())).thenReturn(Optional.of("def456"));

    String other = quotes.fingerprint(task, "in1");

    assertThat(first).hasSize(32).isEqualTo(again).isNotEqualTo(other);
  }

  @Test
  void fingerprintsPlanByItsFileAndInputs(@TempDir Path worktree) {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
    when(facts.plan(any())).thenReturn("step 1");
    String first = quotes.planFingerprint(task, "in1");
    String again = quotes.planFingerprint(task, "in1");
    when(facts.plan(any())).thenReturn("step 1, step 2");

    String other = quotes.planFingerprint(task, "in1");

    assertThat(first).hasSize(32).isEqualTo(again).isNotEqualTo(other);
  }

  @Test
  void quotesTheTicketTheDiffTheDecisionsTheHumanAndTheNotesOfTheRound(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();

    var read =
        fixture.quotingRound(task, "- asked: keep v2? — decided: keep it").round("ABC-1", task);

    assertThat(read)
        .contains(
            new MasterPanel.RoundRead(
                "Summary: accept v3 calls",
                "+int x;\n",
                "- asked: keep v2? — decided: keep it",
                List.of("keep v2"),
                "disputed: the guard is right"));
  }

  @Test
  void quotesPlanRoundWithNoDiff(@TempDir Path worktree) {
    RoundQuotesFixture fixture = new RoundQuotesFixture(worktree, facts);
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();

    var read = fixture.quotingRound(task, "").plan("ABC-1", task);

    assertThat(read)
        .contains(
            new MasterPanel.RoundRead(
                "Summary: accept v3 calls",
                "",
                "",
                List.of("keep v2"),
                "disputed: the guard is right"));
  }

  @Test
  void quotesQuestionRoundWithOnlyTheDecisionsAndWhatTheHumanSaid(@TempDir Path worktree) {
    MasterDecisions decisions = mock(MasterDecisions.class);
    TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
    when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
    when(decisions.of(task)).thenReturn("- settled");

    var read = new RoundQuotes(facts, decisions, reviews).question(task);

    assertThat(read)
        .contains(new MasterPanel.RoundRead("", "", "- settled", List.of("keep v2"), ""));
  }

  @Test
  void quotesNoRoundWhereWhatTheHumanTypedCouldNotBeRead(@TempDir Path worktree) {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    when(facts.humanSaid(task)).thenReturn(Optional.empty());

    assertThat(quotes.round("ABC-1", task)).isEmpty();
    assertThat(quotes.plan("ABC-1", task)).isEmpty();
    assertThat(quotes.question(task)).isEmpty();
  }

  @Test
  void passesOnTheChangedPathsTheOwnDiffAndThePlanAsTheFactsReadThem(@TempDir Path worktree) {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
    when(facts.paths(task)).thenReturn(Optional.of(List.of("A.java")));
    when(facts.ownDiff(task)).thenReturn(Optional.of("abc123"));
    when(facts.plan(task)).thenReturn("1. add the v3 route");

    assertThat(quotes.paths(task)).contains(List.of("A.java"));
    assertThat(quotes.ownDiff(task)).contains("abc123");
    assertThat(quotes.planFile(task)).isEqualTo("1. add the v3 route");
  }
}
