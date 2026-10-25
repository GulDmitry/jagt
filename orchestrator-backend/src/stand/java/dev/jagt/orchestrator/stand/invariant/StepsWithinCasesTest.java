package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.CaseBook;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StepsWithinCasesTest {

    private static final Instant MOVED = Instant.parse("2026-10-25T10:22:53.991Z");

    private static List<Violation> check(Path dir, String logLine) throws Exception {
        CaseBook book = CaseBook.parse("session-cases.md.dist", Files.readString(Path.of("../session-cases.md.dist")));
        Path log = dir.resolve("jagt-backend.log");
        Files.writeString(log, logLine + "\n");
        RunRecord run = new RunRecord(
                Map.of("ABC-5", List.of(new RunRecord.Step(TaskStatus.REVIEW_PENDING, MOVED.minusSeconds(60), "MCP"),
                        new RunRecord.Step(TaskStatus.IN_PROGRESS, MOVED, "MASTER"))),
                List.of(), new RunRecord.Timeline(List.of(), BackendLog.relays(log), Set.of()), Map.of());
        return new StepsWithinCases(book).check(run);
    }

    @Test
    void aMasterRoundNotReadyIsWithinItsCaseAtTheStatusTheDoorChecked(@TempDir Path dir) throws Exception {
        List<Violation> violations = check(dir, """
                {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","status":"REVIEW_PENDING","turn":3}""");

        assertThat(violations).isEmpty();
    }

    @Test
    void aStepOpenedAtAStatusNoCaseNamesIsAViolationAtThatStatus(@TempDir Path dir) throws Exception {
        List<Violation> violations = check(dir, """
                {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","status":"IN_PROGRESS","turn":3}""");

        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation.evidence())
                .startsWith("round-not-ready at IN_PROGRESS"));
    }

    @Test
    void aLineWithoutAStatusIsReadAtTheStatusTheHistoryHeldAtThatTime(@TempDir Path dir) throws Exception {
        List<Violation> violations = check(dir, """
                {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","turn":3}""");

        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation.evidence())
                .startsWith("round-not-ready at IN_PROGRESS"));
    }
}
