package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.role.MasterPhase;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.master.Master;
import dev.jagt.orchestrator.task.turn.Turn;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MasterReviewTest {

  private static final TaskReducer reducer =
      new TaskReducer(new TaskChart(Composition.builtIn()), stale -> {});

  @TempDir Path worktree;
  private final MasterReview stateless = new MasterReview(mock(StateService.class));
  private StateService state;
  private MasterReview reviews;
  private TaskState task;
  private TaskState taskInRound5;

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
            reducer,
            event -> {});
    reviews = new MasterReview(state);
    task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build();
    taskInRound5 = task.withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()));
  }

  @Test
  void readsNothingFromTaskTheReviewerHasNotWrittenAbout() {

    assertThat(stateless.of(task)).isEmpty();
  }

  @Test
  void neverReadsVerdictBackOutOfTheFileWrittenForTheHuman() throws Exception {
    Files.writeString(worktree.resolve(MasterReview.FILE), "nothing wrong\n\nVERDICT: ready\n");

    assertThat(stateless.of(task)).isEmpty();
  }

  @Test
  void readsBackTheFindingsEveryRoleAnsweredWithTheRoleThatMadeThem() {
    Finding inverted =
        new Finding(
            "Foo.java",
            "the guard is inverted",
            "inverted guard",
            "blocking",
            "check()",
            "behaviour");
    state.putTask("a1", task);
    reviews.write(
        "a1",
        task,
        new MasterReview.Review(
            1,
            "",
            "",
            "",
            List.of(
                new MasterReview.Read(
                    "acceptance",
                    new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE)),
                new MasterReview.Read(
                    "developer",
                    new Judgement(
                        "", Verdict.NOT_READY, List.of(inverted), "", List.of(), "", Press.NONE)))),
        "");

    var verdict = reviews.of(state.task("a1").orElseThrow()).orElseThrow();

    assertThat(verdict.kind()).isEqualTo(MasterReview.Kind.NOT_READY);
    assertThat(verdict.findings()).containsExactly(new MasterReview.Said("developer", inverted));
  }

  @Test
  void writesTheFileForTheHumanBesideTheVerdict() {

    stateless.write(
        "a1", task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n");

    assertThat(worktree.resolve(MasterReview.FILE)).content().isEqualTo("VERDICT: ready\n");
  }

  @Test
  void doesNotTakeVerdictAskedAboutAnEarlierHandBackForTheOneInFrontHoweverLateItWasWritten() {
    TaskState handedBackAgain =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build()
            .withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()));
    state.putTask("a1", handedBackAgain);
    reviews.write("a1", handedBackAgain, new MasterReview.Review(3, "", "", "", List.of()), "");

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void doesNotTakeVerdictForHandBackTheSessionMadeAgainWithNoRelayBetween() {
    Turn handedBack = new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of());
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build()
            .withTurn(handedBack);
    state.putTask("a1", task);
    reviews.write("a1", task, new MasterReview.Review(5, "", "", "", List.of()), "");

    TaskState handedBackAgain =
        state.task("a1").orElseThrow().withTurn(handedBack.closed(6_000).closed(7_000));
    state.putTask("a1", handedBackAgain);

    reviews.settle("a1", handedBackAgain);

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void theMasterReadsTheRoundAgainWhereTheVerdictFileWasDeleted() throws Exception {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");
    Files.delete(worktree.resolve(".jagt").resolve(MasterReview.STORED));

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void theMasterReadsTheRoundAgainWhereTheVerdictFileWasRewritten() throws Exception {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");
    Files.writeString(
        worktree.resolve(".jagt").resolve(MasterReview.STORED), "{\"round\":5,\"reads\":[]}");

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void theMasterKeepsReadingTheRoundWhereTheVerdictFileCouldNotBeRead() throws Exception {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");
    Files.setPosixFilePermissions(worktree.resolve(".jagt").resolve(MasterReview.STORED), Set.of());

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void theMasterStandsInWhereTheVerdictFileItSealedDoesNotParse() throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    Files.writeString(worktree.resolve(".jagt").resolve(MasterReview.STORED), "not json");
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build()
            .withTurn(new Turn("round-not-ready", 5, "", 5_000, Map.of(), List.of()))
            .withMaster(
                Master.defaults()
                    .withSeal(
                        JagtJson.fingerprint(
                            "not json".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        5));
    state.putTask("a1", task);

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.STANDS_IN);
  }

  @Test
  void theMasterReadsVerdictSealedWithoutItsRoundAsTheRoundItAnswers() throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    String stored = "{\"round\":3,\"reads\":[]}";
    Files.writeString(worktree.resolve(".jagt").resolve(MasterReview.STORED), stored);
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build()
            .withTurn(new Turn("round-not-ready", 3, "", 5_000, Map.of(), List.of()))
            .withMaster(
                Master.defaults()
                    .withSeal(
                        JagtJson.fingerprint(
                            stored.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        0));
    state.putTask("a1", task);

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void theMasterKeepsSealWrittenWhileTheCallerStillHeldTheOlderOne() {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");
    TaskState older =
        state.task("a1").orElseThrow().withMaster(Master.defaults().withSeal("older", 5));

    reviews.settle("a1", older);

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void theMasterKeepsSealWrittenWhileTheCallerSawTheVerdictFileGone() throws Exception {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");
    TaskState older =
        state.task("a1").orElseThrow().withMaster(Master.defaults().withSeal("older", 5));
    Files.delete(worktree.resolve(".jagt").resolve(MasterReview.STORED));

    reviews.settle("a1", older);

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void theMasterHoldsReadTheRoundWhereTheVerdictFileStandsAsJagtWroteIt() {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(5, "", "", "", List.of()), "");

    reviews.settle("a1", state.task("a1").orElseThrow());

    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void makesRestampedVerdictAnswerTheHandBackInFront() {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(3, "abc123", "", "", List.of()), "");

    boolean restamped = reviews.restamp("a1", state.task("a1").orElseThrow());

    assertThat(restamped).isTrue();
    assertThat(MasterPhase.of(state.task("a1").orElseThrow())).isEqualTo(MasterPhase.READ);
  }

  @Test
  void saysNothingWasRestampedWhereNoVerdictExists() {

    assertThat(stateless.restamp("a1", task)).isFalse();
  }

  @Test
  void saysVerdictInWordsWithSpaceWhereTheKindHasAnUnderscore() {
    assertThat(
            new MasterReview.Verdict(
                    MasterReview.Kind.NOT_READY, List.of(), List.of(), List.of(), 1, "")
                .said())
        .isEqualTo("not ready");
  }

  @Test
  void saysNothingWasWrittenForTaskWithNoWorktree() {
    TaskState task = TaskState.builder("proj", "  ", TaskStatus.REVIEW_PENDING).alias("a1").build();

    assertThat(
            stateless.write(
                "a1", task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n"))
        .isFalse();
  }

  @Test
  void saysTheVerdictWasNotWrittenWhereOnlyTheFileForTheHumanCould() throws Exception {
    Files.writeString(worktree.resolve(".jagt"), "in the way");

    boolean written =
        stateless.write(
            "a1", task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n");

    assertThat(written).isFalse();
    assertThat(worktree.resolve(MasterReview.FILE)).content().isEqualTo("VERDICT: ready\n");
  }

  @Test
  void saysBothFilesWereWrittenWhereTheyWere() {

    assertThat(
            stateless.write(
                "a1", task, new MasterReview.Review(1, "", "", "", List.of()), "VERDICT: ready\n"))
        .isTrue();
  }

  @Test
  void loadsVerdictStoredBeforeItKeptTheReportKindAsOneThatMatchesNoHandBack() throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    String stored = "{\"round\":3,\"ownDiff\":\"abc123\",\"reads\":[]}";
    Files.writeString(worktree.resolve(".jagt").resolve(MasterReview.STORED), stored);
    TaskState unsealed =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .build();
    TaskState task =
        unsealed.withMaster(
            Master.defaults()
                .withSeal(
                    JagtJson.fingerprint(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    0));

    MasterReview.Review review = stateless.stored(task).orElseThrow();

    assertThat(review.readsInputsOf(task, "abc123", "in1")).isFalse();
  }

  @Test
  void remembersTheKindOfTheHandBackItReadBesideTheOwnDiff() {
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .alias("a1")
            .report(dev.jagt.orchestrator.task.turn.AgentReport.NO_CHANGES)
            .build();
    state.putTask("a1", task);
    reviews.write("a1", task, MasterReview.Review.of(task, "abc123", "in1", List.of()), "");

    MasterReview.Review stored = reviews.stored(state.task("a1").orElseThrow()).orElseThrow();

    assertThat(stored.readsInputsOf(task, "abc123", "in1")).isTrue();
    assertThat(stored.readsInputsOf(task, "def456", "in1")).isFalse();
  }

  @Test
  void takesNoVerdictFromFileJagtDidNotWriteHoweverItNamesTheRoundInFront() throws Exception {
    state.putTask("a1", taskInRound5);
    reviews.write("a1", taskInRound5, new MasterReview.Review(4, "", "", "", List.of()), "");
    Files.writeString(
        worktree.resolve(".jagt").resolve(MasterReview.STORED), "{\"round\":5,\"reads\":[]}");

    TaskState sealed = state.task("a1").orElseThrow();

    assertThat(reviews.of(sealed)).isEmpty();
  }

  @Test
  void takesNoVerdictFromFileWhenJagtStoredNoFingerprintForIt() throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    Files.writeString(
        worktree.resolve(".jagt").resolve(MasterReview.STORED), "{\"round\":0,\"reads\":[]}");

    assertThat(stateless.of(task)).isEmpty();
  }

  @Test
  void asksTheHumanWhereNoRoleReadTheRound() {
    var verdict = MasterReview.Verdict.of(new MasterReview.Review(1, "", "", "", List.of()));

    assertThat(verdict.ready()).isFalse();
    assertThat(verdict.kind()).isEqualTo(MasterReview.Kind.QUESTION);
  }

  @Test
  void takesOnlyReadyVerdictAsReady() {
    var ready =
        new MasterReview.Verdict(MasterReview.Kind.READY, List.of(), List.of(), List.of(), 1, "");
    var notReady =
        new MasterReview.Verdict(
            MasterReview.Kind.NOT_READY, List.of(), List.of(), List.of(), 1, "");

    assertThat(ready.ready()).isTrue();
    assertThat(notReady.ready()).isFalse();
  }
}
