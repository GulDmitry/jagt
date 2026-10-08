package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketPlacementTest {

    private final TicketReader tickets = mock(TicketReader.class);
    private final ProjectRouting routing = mock(ProjectRouting.class);
    private final TicketPlacement placement = new TicketPlacement(tickets, routing);

    @Test
    void refusesAnItemTheRouterCouldNotPlaceRatherThanPickingARepository() {
        when(routing.projectFor(any())).thenReturn(new ProjectRouting.Undecided("placed in no configured project"));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42");

        var outcome = placement.place("ABC-42", new Answer<>(Optional.of(item), TokenUsage.NONE), null);

        assertThat(outcome).isEqualTo(new TicketPlacement.Refused("error: ABC-42 not placed in a configured project:"
                + " placed in no configured project — say which: do ABC-42 <project>"));
    }

    @Test
    void asksTheRouterWhereToPutAnItemNobodyNamedAProjectFor() {
        when(routing.projectFor(any())).thenReturn(new ProjectRouting.Placed("group-a"));
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42");

        var outcome = placement.place("ABC-42", new Answer<>(Optional.of(item), TokenUsage.NONE), null);

        assertThat(outcome).isEqualTo(new TicketPlacement.Placed(item, List.of("group-a")));
    }

    @Test
    void asksNoRouterWhereAHumanNamedTheProjects() {
        TicketFacts item = TicketFacts.defaults().withExists(true).withKey("ABC-42").withTitle("Widget layout is off")
                .withUrl("https://tracker/ABC-42");

        var outcome = placement.place("ABC-42", new Answer<>(Optional.of(item), TokenUsage.NONE),
                List.of("web", "api"));

        assertThat(outcome).isEqualTo(new TicketPlacement.Placed(item, List.of("web", "api")));
        verify(routing, never()).projectFor(any());
    }

    @Test
    void refusesWhenTheTrackerSaysThereIsNoSuchItem() {
        var outcome = placement.place("ABC-42", new Answer<>(Optional.of(TicketFacts.defaults()),
                TokenUsage.ofCall(38_000, 0, 60, 0.41)), null);

        assertThat(outcome).isEqualTo(new TicketPlacement.Refused(
                "error: no such item: ABC-42 (the tracker says so) — no task created"));
    }

    @Test
    void saysTheReadFailedInsteadOfCallingTheTicketMissing() {
        var outcome = placement.place("ABC-42", Answer.unavailable(), null);

        assertThat(outcome).isEqualTo(new TicketPlacement.Refused(
                "error: read failed: ABC-42 (cause in the log) — no task created"));
    }

    @Test
    void refusesWhenTheReadAnsweredAboutADifferentItem() {
        TicketFacts other = TicketFacts.defaults().withExists(true).withKey("ABC-99").withTitle("Widget layout is off")
                .withTrackerProject("ABC").withUrl("https://tracker/ABC-99");

        var outcome = placement.place("ABC-42", new Answer<>(Optional.of(other), TokenUsage.NONE), null);

        assertThat(((TicketPlacement.Refused) outcome).message())
                .contains("asked for ABC-42 and got ABC-99 back", "no task created");
    }

    @Test
    void chargesTheReadToTheTaskItNamed() {
        TokenUsage spent = TokenUsage.ofCall(25_000, 0, 170, 0.05);

        placement.created("ABC-123", null, spent);

        verify(tickets).charge("ABC-123", spent);
    }

    @Test
    void tellsTheRouterOnlyOfAPlacementAHumanMade() {
        placement.created("ABC-123", null, TokenUsage.NONE);

        verify(routing, never()).placedByHand(anyString(), anyString());
    }

    @Test
    void tellsTheRouterWhereAHumanPlacedTheItem() {
        placement.created("ABC-123", List.of("web", "api"), TokenUsage.NONE);

        verify(routing).placedByHand("ABC-123", "web");
    }
}
