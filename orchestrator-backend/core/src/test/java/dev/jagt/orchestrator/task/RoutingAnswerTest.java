package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoutingAnswerTest {

  @ParameterizedTest
  @CsvSource({"api,labels carry it,true", "api,'  ',false"})
  void teachesOnlyWhenTheAnswerCameWithRule(String project, String rule, boolean teaches) {
    assertThat(new RoutingAnswer(project, rule).teaches()).isEqualTo(teaches);
  }

  @Test
  void stripsWhatTheRouterPaddedItsAnswerWith() {
    RoutingAnswer answer = new RoutingAnswer(" api ", " labels carry it ");

    assertThat(answer.project()).isEqualTo("api");
    assertThat(answer.rule()).isEqualTo("labels carry it");
  }
}
