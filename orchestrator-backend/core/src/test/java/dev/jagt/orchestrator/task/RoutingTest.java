package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoutingTest {

  @Test
  void answersQuestionNamingAnItemAndAtLeastOneProject() {
    RoutingQuestion question =
        RoutingQuestion.defaults()
            .withItem(TicketFacts.defaults().withKey("ABC-42"))
            .withProjects(Map.of("api", "the backend"));

    assertThat(question.answerable()).isTrue();
  }

  @Test
  void cannotAnswerQuestionWithNoProjectToChooseFrom() {
    RoutingQuestion question =
        RoutingQuestion.defaults().withItem(TicketFacts.defaults().withKey("ABC-42"));

    assertThat(question.answerable()).isFalse();
  }

  @Test
  void cannotAnswerQuestionWhoseItemHasNoKey() {
    RoutingQuestion question =
        RoutingQuestion.defaults().withProjects(Map.of("api", "the backend"));

    assertThat(question.answerable()).isFalse();
  }

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

  @Test
  void stripsWhatTheRouterPaddedTheRulePairWith() {
    RulePair pair = new RulePair(" labels carry it ", " other ");

    assertThat(pair.kept()).isEqualTo("labels carry it");
    assertThat(pair.duplicate()).isEqualTo("other");
  }
}
