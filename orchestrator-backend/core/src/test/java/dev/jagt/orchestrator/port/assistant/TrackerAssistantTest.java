package dev.jagt.orchestrator.port.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrackerAssistantTest {

  @Test
  void readsTicketWithNoCorrectionsUnlessTheCallerHadSome() {
    List<List<String>> asked = new java.util.ArrayList<>();
    TrackerAssistant tracker =
        new TrackerAssistant() {
          @Override
          public Answer<dev.jagt.orchestrator.task.launch.TicketFacts> readTicket(
              String ticketRef, List<String> corrections) {
            asked.add(corrections);
            return Answer.unavailable();
          }

          @Override
          public Answer<String> readTicketText(String ticketRef) {
            return Answer.unavailable();
          }

          @Override
          public Answer<List<String>> findCandidates(String query) {
            return Answer.unavailable();
          }
        };

    tracker.readTicket("ABC-42");

    assertThat(asked).containsExactly(List.of());
  }
}
