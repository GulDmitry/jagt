package dev.jagt.orchestrator.task.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingQuestionTest {

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
}
