package dev.jagt.orchestrator.port;

import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TrackerAssistantTest {

    private static final class Recording implements TrackerAssistant {

        private final List<List<String>> asked = new ArrayList<>();

        @Override
        public Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections) {
            asked.add(corrections);
            return new Answer<>(Optional.of(TicketFacts.defaults().withKey(ticketRef)), TokenUsage.NONE);
        }

        @Override
        public Answer<String> readTicketText(String ticketRef) {
            return Answer.unavailable();
        }

        @Override
        public Answer<List<String>> findCandidates(String query) {
            return Answer.unavailable();
        }
    }

    @Test
    void asksAFirstReadWithNoCorrectionsAndHandsBackWhatItAnswered() {
        Recording tracker = new Recording();

        Answer<TicketFacts> answer = tracker.readTicket("ABC-42");

        assertThat(answer.facts().orElseThrow().key()).isEqualTo("ABC-42");
        assertThat(tracker.asked).containsExactly(List.of());
    }
}
