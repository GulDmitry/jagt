package dev.jagt.orchestrator.service.master;

import static dev.jagt.orchestrator.service.master.TestMasterReviewBuilder.masterReview;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class RoundQuotesTest {

  private final RoundFacts facts = mock(RoundFacts.class);

  @TempDir Path worktree;

  private StateService state;
  private MasterReview reviews;
  private RoundQuotes quotes;
  private TaskState pending;

  @BeforeEach
  void setUp() {
    state =
        new StateService(
            new JsonMapper(),
            new OrchestratorPaths(
                OrchestratorProperties.defaults()
                    .withRoot(worktree.toString())
                    .withStateFile(worktree.resolve("state.json").toString()),
                FakeHost.machine()),
            new TaskReducer(new TaskChart(Composition.builtIn())),
            event -> {});
    reviews = new MasterReview(state);
    quotes = new RoundQuotes(facts, mock(MasterDecisions.class), reviews, mock(NamedTickets.class));
    pending =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .lastActiveTimestamp(5_000)
            .build();
  }

  @Test
  void keepsReadyVerdictWhenOnlyTheBaseMovedUnderTheTasksOwnDiff() {
    state.putTask("a1", pending);
    reviews.write("a1", pending, masterReview().build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = quotes.carried("a1", task, "in1");

    assertThat(carried).isTrue();
    task = state.task("a1").orElseThrow();
    assertThat(MasterPhase.of(task)).isEqualTo(MasterPhase.READ);
  }

  @Test
  void readsTheRoundAnewWhenTheTasksOwnDiffChanged() {
    state.putTask("a1", pending);
    reviews.write("a1", pending, masterReview().build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("def456"));

    boolean carried = quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
    assertThat(MasterPhase.of(task)).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void keepsNotReadyVerdictWhenTheSessionHandedBackTheSameOwnDiffAndKind() {
    TaskState handedBack =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .report(AgentReport.NO_CHANGES)
            .lastActiveTimestamp(5_000)
            .build();
    state.putTask("a1", handedBack);
    reviews.write(
        "a1",
        handedBack,
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
                        List.of(new Finding("Foo.java", "drops v2", "", "wrong", "", "missing")),
                        "",
                        List.of(),
                        "",
                        "")))),
        "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = quotes.carried("a1", task, "in1");

    assertThat(carried).isTrue();
    task = state.task("a1").orElseThrow();
    assertThat(reviews.of(task).orElseThrow().kind()).isEqualTo(MasterReview.Kind.NOT_READY);
  }

  @Test
  void readsTheRoundAnewWhenTheHandBackIsOfAnotherKind() {
    TaskState handedBack =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .report(AgentReport.NO_CHANGES)
            .lastActiveTimestamp(5_000)
            .build();
    state.putTask("a1", handedBack);
    reviews.write("a1", handedBack, masterReview().build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhereTheVerdictWasStoredBeforeItKeptTheReportKind() {
    state.putTask("a1", pending);
    reviews.write("a1", pending, masterReview().withKind(null).build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    boolean carried = quotes.carried("a1", task, "in1");

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhenTheHumanSaidSomethingNewOverTheSameDiffAndKind() {
    when(facts.ask(eq("ABC-1"), any())).thenReturn("Summary");
    when(facts.notes(any())).thenReturn("");
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of("keep v2")));
    String read = quotes.inputs("ABC-1", pending).orElseThrow();
    state.putTask("a1", pending);
    reviews.write("a1", pending, masterReview().withInputs(read).build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of("keep v2", "drop v1")));

    boolean carried = quotes.carried("a1", task, quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @Test
  void readsTheRoundAnewWhenTheSessionsNotesChangedOverTheSameDiffAndKind() {
    when(facts.ask(eq("ABC-1"), any())).thenReturn("Summary");
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of()));
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.notes(any())).thenReturn("");
    String read = quotes.inputs("ABC-1", pending).orElseThrow();
    state.putTask("a1", pending);
    reviews.write("a1", pending, masterReview().withInputs(read).build(), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.notes(any())).thenReturn("disputed: the guard is right");

    boolean carried = quotes.carried("a1", task, quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isFalse();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NONE",
      value = {
        "Summary: accept v3 calls, NONE, true",
        "accept v3 calls, accept v4 calls, false",
        "NONE, accept v3 calls, false"
      })
  void keepsVerdictOnlyWhileTheTicketItReadIsUnchangedOrRestartForgotIt(
      String ticketRead, String ticketNow, boolean expected) {
    when(facts.ticket("ABC-1")).thenReturn(Optional.ofNullable(ticketRead));
    when(facts.request(any())).thenReturn("Summary");
    when(facts.notes(any())).thenReturn("");
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    when(facts.humanSaid(any())).thenReturn(Optional.of(List.of()));
    state.putTask("a1", pending);
    reviews.write(
        "a1",
        pending,
        masterReview().withInputs(quotes.inputs("ABC-1", pending).orElseThrow()).build(),
        "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ticket("ABC-1")).thenReturn(Optional.ofNullable(ticketNow));

    boolean carried = quotes.carried("a1", task, quotes.inputs("ABC-1", task).orElseThrow());

    assertThat(carried).isEqualTo(expected);
  }

  @Test
  void callsTheFirstReadOfRoundFirst() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));

    String trigger = quotes.trigger(task, "in1");

    assertThat(trigger).isEqualTo("first");
  }

  @Test
  void callsRereadOfTheSameInputsRetryAndChangedDiffChange() {
    state.putTask("a1", pending);
    reviews.write(
        "a1", pending, new MasterReview.Review(1_000, "abc123", "PLAIN", "in1", List.of()), "");
    TaskState task = state.task("a1").orElseThrow();
    when(facts.ownDiff(any())).thenReturn(Optional.of("abc123"));
    String sameInputs = quotes.trigger(task, "in1");
    when(facts.ownDiff(any())).thenReturn(Optional.of("def456"));

    String changedDiff = quotes.trigger(task, "in1");

    assertThat(sameInputs).isEqualTo("retry");
    assertThat(changedDiff).isEqualTo("changed");
  }

  @Test
  void fingerprintsRoundByItsOwnDiffAndInputs() {
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
  void fingerprintsPlanByItsFileAndInputs() {
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
  void quotesTheTicketTheDiffTheDecisionsTheHumanAndTheNotesOfTheRound() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    MasterDecisions decisions = mock(MasterDecisions.class);
    when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
    when(facts.ask("ABC-1", task)).thenReturn("Summary: accept v3 calls");
    when(facts.diff(task)).thenReturn("+int x;\n");
    when(facts.notes(task)).thenReturn("disputed: the guard is right");
    when(decisions.of(task)).thenReturn("- asked: keep v2? — decided: keep it");

    var read =
        new RoundQuotes(facts, decisions, reviews, mock(NamedTickets.class)).round("ABC-1", task);

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
  void quotesPlanRoundWithNoDiff() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.PLAN_PENDING).build();
    MasterDecisions decisions = mock(MasterDecisions.class);
    when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
    when(facts.ask("ABC-1", task)).thenReturn("Summary: accept v3 calls");
    when(facts.notes(task)).thenReturn("disputed: the guard is right");
    when(decisions.of(task)).thenReturn("");

    var read =
        new RoundQuotes(facts, decisions, reviews, mock(NamedTickets.class)).plan("ABC-1", task);

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
  void quotesQuestionRoundWithOnlyTheDecisionsAndWhatTheHumanSaid() {
    MasterDecisions decisions = mock(MasterDecisions.class);
    TaskState task = TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS).build();
    when(facts.humanSaid(task)).thenReturn(Optional.of(List.of("keep v2")));
    when(decisions.of(task)).thenReturn("- settled");

    var read = new RoundQuotes(facts, decisions, reviews, mock(NamedTickets.class)).question(task);

    assertThat(read)
        .contains(new MasterPanel.RoundRead("", "", "- settled", List.of("keep v2"), ""));
  }

  @Test
  void quotesNoRoundWhereWhatTheHumanTypedCouldNotBeRead() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING).build();
    when(facts.humanSaid(task)).thenReturn(Optional.empty());

    assertThat(quotes.round("ABC-1", task)).isEmpty();
    assertThat(quotes.plan("ABC-1", task)).isEmpty();
    assertThat(quotes.question(task)).isEmpty();
  }

  @Test
  void passesOnTheChangedPathsTheOwnDiffAndThePlanAsTheFactsReadThem() {
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
