package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectCards;
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
        ProjectCards.Card card = new ProjectCards.Card("demo", "/work/demo", "git@code.example:acme/demo.git", null,
                "origin/main", "dev", List.of("web"), null, null, null, null, null, null, null);
        when(cards.of("demo")).thenReturn(card);

        assertThat(controller.card("demo")).isSameAs(card);
    }
}
