package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ProjectCards;
import dev.jagt.orchestrator.task.ProjectConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectCardControllerTest {

  private final ProjectCards cards = mock(ProjectCards.class);
  private final ProjectCardController controller = new ProjectCardController(cards);

  @Test
  void servesTheCardOfTheProjectTheHumanHovers() {
    ProjectCards.Card card =
        new ProjectCards.Card(
            "demo",
            "code.example:acme/demo",
            new ProjectConfig.Repository("/work/demo", List.of("web"), null, List.of()),
            new ProjectConfig.Branches("origin/main", "dev", null),
            new ProjectConfig.CodeHost(
                null, null, List.of(), List.of(), List.of(), List.of(), List.of()),
            new ProjectConfig.Verify(List.of(), null));
    when(cards.of("demo")).thenReturn(card);

    assertThat(controller.card("demo")).isSameAs(card);
  }
}
