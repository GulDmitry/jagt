package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.port.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.RoundReviewer.Premise;
import dev.jagt.orchestrator.service.master.MasterReview.Read;
import dev.jagt.orchestrator.service.master.MasterReview.Review;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MasterVerdictReportTest {

    @Test
    void aFailingCaseShowsEachRolesFindingsAndReasoningBesideWhatWasExpected() {
        Review review = new Review(7, "", List.of(
                new Read("architect", new Judgement("", "not_ready", List.of(
                        new Finding("A.java", "leaks the caller", "", "blocking", "run", "leak")),
                        "", List.of(new Premise("A names its caller", "grep: A.java:3")), "")),
                new Read("qa", new Judgement("", "question", List.of(), "which test pins it?",
                        List.of(new Premise("no test", "")), "")),
                new Read("designer", Judgement.failed("no reply"))));

        String report = MasterVerdictReport.describe("READY, naming [leak]", review);

        assertThat(report).isEqualTo("""
                expected: READY, naming [leak]
                stored verdict, round 7:
                - architect: not_ready
                    finding [blocking] A.java run - leaks the caller
                    premise: A names its caller (proven by: grep: A.java:3)
                - qa: question
                    premise: no test (reasoned only)
                    question: which test pins it?
                - designer: could not read the round: no reply""");
    }

    @Test
    void theStoredVerdictIsReadFromTheWorktreesJagtDirectory(@TempDir Path worktree) throws Exception {
        Files.createDirectories(worktree.resolve(".jagt"));
        Review review = new Review(2, "", List.of(new Read("developer", new Judgement("", "ready", List.of(),
                "", List.of(), ""))));
        new JsonMapper().writeValue(worktree.resolve(".jagt/master-review.json").toFile(), review);

        String report = MasterVerdictReport.of("NOT_READY", worktree);

        assertThat(report).isEqualTo("expected: NOT_READY\nstored verdict, round 2:\n- developer: ready");
    }

    @Test
    void aMissingStoredVerdictIsNamedAsMissing(@TempDir Path worktree) {
        String report = MasterVerdictReport.of("READY", worktree);

        assertThat(report).isEqualTo("READY\nno master-review.json under " + worktree);
    }
}
