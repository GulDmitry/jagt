package dev.jagt.orchestrator.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeReviewAnswerTest {

  @Test
  void shipsBetweenTwelveAndFifteenCasesEachNamingWhatItExpects() {
    List<CodeReviewCase> cases = CodeReviewCases.all();

    assertThat(cases).hasSizeBetween(12, 15);
    assertThat(cases)
        .allSatisfy(review ->
            assertThat(review.expected().clean() || !review.expected().findings().isEmpty())
                .isTrue());
  }

  @Test
  void meetsCaseWhenFindingNamesExpectedFileAndWords() {
    CodeReviewCase review =
        CodeReviewCases.parse(
            """
            case: a query built from input
            description: add a search
            diff: x
            expect:
              verdict: findings
              findings:
                - file: OrderRepository.java
                  pattern: "inject"
                  severity: [blocking]
            """);
    CodeReviewAnswer answer =
        CodeReviewAnswer.parse(
            """
            ```json
            {"verdict": "findings", "findings": [{"file": "src/app/OrderRepository.java",
            "issue": "SQL injection through name", "pattern": "string concatenation",
            "severity": "blocking", "anchor": "sql", "kind": "injection"}]}
            ```
            """);

    assertThat(answer.problems(review.expected())).isEmpty();
  }

  @Test
  void stopsCleanCaseWhenFindingBlocks() {
    CodeReviewCase review =
        CodeReviewCases.parse(
            """
            case: a rename
            description: rename
            diff: x
            expect:
              verdict: ready
            """);
    CodeReviewAnswer answer =
        CodeReviewAnswer.parse(
            """
            {"verdict": "findings", "findings": [{"file": "A.java", "issue": "odd",
            "pattern": "p", "severity": "blocking", "anchor": "a", "kind": "k"}]}
            """);

    assertThat(answer.problems(review.expected())).hasSize(1);
  }
}
