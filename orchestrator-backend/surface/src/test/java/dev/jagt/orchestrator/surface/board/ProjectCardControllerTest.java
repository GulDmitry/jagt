package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectCards;
import dev.jagt.orchestrator.task.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectCardControllerTest {

    private final ProjectCards cards = mock(ProjectCards.class);
    private final ProjectCardController controller = new ProjectCardController(cards);

    @Test
    void servesTheCardOfTheProjectTheHumanHovers() {
        ProjectCards.Card card = new ProjectCards.Card("demo", "code.example:acme/demo",
                new ProjectConfig.Repository("/work/demo", List.of("web"), null, null),
                new ProjectConfig.Branches("origin/main", "dev", null),
                new ProjectConfig.CodeHost(null, null, null, null, null, null),
                new ProjectConfig.Verify(null, null));
        when(cards.of("demo")).thenReturn(card);

        assertThat(controller.card("demo")).isSameAs(card);
    }
}
