package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.assistant.TicketReader;
import dev.jagt.orchestrator.task.ticket.TicketFacts;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The tracker's own word on every other item a session's question names. */
@Component
@RequiredArgsConstructor
public class NamedTickets {

  static final int MOST = 5;

  private final TicketReader tickets;
  private final TrackerWorkflow workflow;

  /** {@code status} blank where the tracker could not be read. */
  public record Fact(String key, String status, boolean closes) {

    public boolean read() {
      return !status.isBlank();
    }

    public String line() {
      return key
          + ": status \""
          + status
          + "\", at the stage that closes work: "
          + (closes ? "yes" : "no");
    }
  }

  /** An item the tracker says does not exist is no item: a word that only looks like a key. */
  public List<Fact> of(String taskId, String question) {
    List<String> keys =
        Arrays.stream(question.split("[^A-Za-z0-9-]+"))
            .filter(workflow::isItemKey)
            .filter(key -> !key.equalsIgnoreCase(taskId))
            .distinct()
            .toList();
    return IntStream.range(0, keys.size())
        .mapToObj(at ->
            at < MOST ? read(taskId, keys.get(at)) : Optional.of(new Fact(keys.get(at), "", false)))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<Fact> read(String taskId, String key) {
    Answer<TicketFacts> answer = tickets.read(key);
    tickets.charge(taskId, answer.usage());
    if (answer.facts().filter(found -> !found.exists()).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        answer
            .facts()
            .filter(TicketFacts::usable)
            .map(found -> new Fact(key, found.trackerStatus(), workflow.closesWork(found)))
            .orElse(new Fact(key, "", false)));
  }
}
