package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Each task's ticket as first read, kept beside `state.json`: the model's copy is not byte-stable,
 * so a second read would change the round's inputs.
 */
@Component
@RequiredArgsConstructor
public class TicketTexts {

  private final TrackerAssistant assistant;
  private final UsageTracker usage;
  private final KeptTicketTexts kept;

  /** A failed read is booked too: asking again every tick is paid for every tick. */
  public void read(String taskId, String ticketRef, long asOf) {
    Answer<String> answer = assistant.readTicketText(ticketRef);
    usage.chargeTask(taskId, answer.usage());
    kept.markRead(taskId, asOf);
    answer.facts().ifPresent(text -> kept.keep(taskId, text));
  }

  public boolean readSince(String taskId, long since) {
    return kept.readSince(taskId, since);
  }

  public Optional<String> of(String taskId) {
    return kept.of(taskId);
  }
}
