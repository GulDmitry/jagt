package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Premise;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.service.master.MasterReview.Read;
import dev.jagt.orchestrator.service.master.MasterReview.Review;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class MasterVerdictReportTest {

  @Test
  void failingCaseShowsEachRolesFindingsAndReasoningBesideWhatWasExpected() {
    Review review =
        new Review(
            7,
            "",
            "",
            "",
            List.of(
                new Read(
                    "architect",
                    new Judgement(
                        "",
                        Verdict.NOT_READY,
                        List.of(
                            new Finding(
                                "A.java", "leaks the caller", "", "blocking", "run", "leak")),
                        "",
                        List.of(new Premise("A names its caller", "grep: A.kt:3")),
                        "",
                        Press.NONE)),
                new Read(
                    "acceptance",
                    new Judgement(
                        "",
                        Verdict.QUESTION,
                        List.of(),
                        "which test pins it?",
                        List.of(new Premise("no test", "")),
                        "",
                        Press.NONE)),
                new Read("designer", Judgement.failed("no reply"))));

    String report = MasterVerdictReport.describe("READY, naming [leak]", review);

    assertThat(report)
        .isEqualTo(
            """
            expected: READY, naming [leak]
            stored verdict, round 7:
            - architect: not ready
                finding [blocking] A.java run - leaks the caller
                premise: A names its caller (proven by: grep: A.kt:3)
            - acceptance: question
                premise: no test (reasoned only)
                question: which test pins it?
            - designer: could not read the round: no reply\
            """);
  }

  @Test
  void theStoredVerdictIsReadFromTheWorktreesJagtDirectory(@TempDir Path worktree)
      throws Exception {
    Files.createDirectories(worktree.resolve(".jagt"));
    Review review =
        new Review(
            2,
            "",
            "",
            "",
            List.of(
                new Read(
                    "developer",
                    new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE))));
    new JsonMapper().writeValue(worktree.resolve(".jagt/master-review.json").toFile(), review);

    String report = MasterVerdictReport.of("NOT_READY", worktree);

    assertThat(report)
        .isEqualTo("expected: NOT_READY\nstored verdict, round 2:\n- developer: ready");
  }

  @Test
  void missingStoredVerdictIsNamedAsMissing(@TempDir Path worktree) {
    String report = MasterVerdictReport.of("READY", worktree);

    assertThat(report).isEqualTo("READY\nno master-review.json under " + worktree);
  }
}
