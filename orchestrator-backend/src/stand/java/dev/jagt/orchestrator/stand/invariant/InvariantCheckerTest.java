package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.CaseBook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvariantCheckerTest {

    @Test
    void findsNothingInARunThatKeptTheFrameAndRecordedAMiss() throws Exception {
        CaseBook book = CaseBook.parse("session-cases.md.dist", Files.readString(Path.of("../session-cases.md.dist")));
        Path root = Path.of(InvariantCheckerTest.class.getResource("/runs/clean").toURI());

        List<Violation> violations = new InvariantChecker(book, (commit, from) -> true).check(RunRecord.read(root));

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "base-moved, base-unchanged, 2026-10-24T00:05:00Z, dddddddd",
            "deploy-branch-by-session, deploy-branch-by-deploy, 2026-10-24T00:06:00Z, eeeeeeee",
            "force-push, no-force-push, 2026-10-24T00:09:59Z, bad01111",
            "illegal-transition, legal-transitions, 2026-10-24T00:10:00Z, IN_PROGRESS -> DEPLOY_CONFLICT by MASTER",
            "sampled-reset, legal-transitions, 2026-10-24T00:10:35Z, ABC-1 NEW owner=AGENT",
            "relay-outside-case, steps-within-cases, 2026-10-24T00:01:00.500Z, deploy at IN_PROGRESS",
            "master-after-miss, miss-stops-the-master, 2026-10-24T00:10:00Z, moved to DEPLOYED by MASTER"
    })
    void namesTheInvariantTheTimeAndTheEvidenceOfARunThatBrokeIt(String run, String invariant, Instant at,
                                                                  String evidence) throws Exception {
        CaseBook book = CaseBook.parse("session-cases.md.dist", Files.readString(Path.of("../session-cases.md.dist")));
        Path root = Path.of(InvariantCheckerTest.class.getResource("/runs/" + run).toURI());

        List<Violation> violations = new InvariantChecker(book, (commit, from) -> !commit.startsWith("bad"))
                .check(RunRecord.read(root));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.invariant()).isEqualTo(invariant);
            assertThat(violation.at()).isEqualTo(at);
            assertThat(violation.evidence()).contains(evidence);
        });
    }
}
