package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoApprovalByJagtTest {

  @Test
  void anyApprovalAttemptIsViolationNamingTheAttempt() {
    RunRecord run =
        TestRunRecordBuilder.runRecord()
            .withApprovals(List.of(new RunRecord.Approval(Instant.EPOCH, "pr review 1 --approve")))
            .build();

    List<Violation> violations = new NoApprovalByJagt().check(run);

    assertThat(violations)
        .singleElement()
        .satisfies(
            violation -> assertThat(violation.evidence()).isEqualTo("pr review 1 --approve"));
  }
}
