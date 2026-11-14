package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.role.CaseBook;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StepsWithinCasesTest {

  private static final Instant MOVED = Instant.parse("2026-10-25T10:22:53.991Z");

  @TempDir Path dir;

  private CaseBook book;
  private Map<String, List<RunRecord.Step>> history;

  @BeforeEach
  void readCasesAndHistory() throws Exception {
    book =
        CaseBook.parse(
            "roles/session/cases.md.dist",
            Files.readString(Path.of("../roles/session/cases.md.dist")));
    history =
        Map.of(
            "ABC-5",
            List.of(
                new RunRecord.Step(TaskStatus.REVIEW_PENDING, MOVED.minusSeconds(60), "MCP"),
                new RunRecord.Step(TaskStatus.IN_PROGRESS, MOVED, "MASTER")));
  }

  @Test
  void masterRoundNotReadyIsWithinItsCaseAtTheStatusTheDoorChecked() throws Exception {
    Path log = dir.resolve("jagt-backend.log");
    Files.writeString(
        log,
        """
        {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","status":"REVIEW_PENDING","turn":3}
        """);

    List<Violation> violations =
        new StepsWithinCases(book)
            .check(
                new RunRecord(
                    history,
                    List.of(),
                    new RunRecord.Timeline(List.of(), BackendLog.relays(log), Set.of()),
                    Map.of()));

    assertThat(violations).isEmpty();
  }

  @Test
  void stepOpenedAtStatusNoCaseNamesIsViolationAtThatStatus() throws Exception {
    Path log = dir.resolve("jagt-backend.log");
    Files.writeString(
        log,
        """
        {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","status":"IN_PROGRESS","turn":3}
        """);

    List<Violation> violations =
        new StepsWithinCases(book)
            .check(
                new RunRecord(
                    history,
                    List.of(),
                    new RunRecord.Timeline(List.of(), BackendLog.relays(log), Set.of()),
                    Map.of()));

    assertThat(violations)
        .singleElement()
        .satisfies(violation ->
            assertThat(violation.evidence()).startsWith("round-not-ready at IN_PROGRESS"));
  }

  @Test
  void lineWithoutStatusIsReadAtTheStatusTheHistoryHeldAtThatTime() throws Exception {
    Path log = dir.resolve("jagt-backend.log");
    Files.writeString(
        log,
        """
        {"@timestamp":"2026-10-25T10:22:53.993Z","message":"turn opened","task":"ABC-5","step":"round-not-ready","turn":3}
        """);

    List<Violation> violations =
        new StepsWithinCases(book)
            .check(
                new RunRecord(
                    history,
                    List.of(),
                    new RunRecord.Timeline(List.of(), BackendLog.relays(log), Set.of()),
                    Map.of()));

    assertThat(violations)
        .singleElement()
        .extracting(Violation::evidence)
        .asString()
        .startsWith("round-not-ready at IN_PROGRESS");
  }
}
