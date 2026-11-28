package dev.jagt.orchestrator.codereviewrun;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.eval.CodeReviewAnswer;
import dev.jagt.orchestrator.eval.CodeReviewCase;
import dev.jagt.orchestrator.eval.CodeReviewCases;
import dev.jagt.orchestrator.eval.CodeReviewReader;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalSpend;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("codeReviewEval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeReviewEvalTest {

  private final CodeReviewReader reader =
      new CodeReviewReader(
          Path.of(
              System.getProperty("codeReviewEval.brief", "../roles/code-reviewer/brief.md.dist")),
          System.getProperty("codeReviewEval.model", "sonnet"));

  @ParameterizedTest
  @MethodSource("cases")
  void readsTheRequestLikeCarefulReviewer(CodeReviewCase review) {
    EvalQuorum.configured()
        .hold(
            review.name(),
            EvalSpend.UNMETERED,
            () -> {
              CodeReviewAnswer answer = reader.read(review);
              assertThat(answer.problems(review.expected())).isEmpty();
            });
  }

  static List<CodeReviewCase> cases() {
    return CodeReviewCases.all();
  }
}
