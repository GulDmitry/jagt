package dev.jagt.orchestrator.port.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoundReviewerTest {

  @ParameterizedTest
  @CsvSource({"blocking,true", "wrong,true", "unproven,true", "unguarded,false", "noise,false"})
  void stopsRoundOnlyForTheFirstThreeSeverities(String severity, boolean stops) {
    assertThat(new RoundReviewer.Finding("A.java", "issue", "pattern", severity, "", "").stops())
        .isEqualTo(stops);
  }

  @Test
  void readsFailedJudgementAsNothingButItsFailure() {
    RoundReviewer.Judgement failed = RoundReviewer.Judgement.failed("no answer");

    assertThat(failed.failure()).isEqualTo("no answer");
    assertThat(failed.findings()).isEmpty();
    assertThat(failed.premises()).isEmpty();
    assertThat(failed.verdict()).isEqualTo(RoundReviewer.Verdict.NOT_READY);
  }
}
