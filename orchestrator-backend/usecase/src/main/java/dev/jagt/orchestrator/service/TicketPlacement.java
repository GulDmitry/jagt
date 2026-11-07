package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Whether a tracker item may become a task, in which projects, and what reading it cost once it
 * has.
 */
@Service
@RequiredArgsConstructor
public class TicketPlacement {

  private final TicketReader tickets;
  private final ProjectRouting routing;
  private final TrackerWorkflow workflow;

  public sealed interface Outcome permits Refused, Placed {}

  public record Refused(String message) implements Outcome {}

  public record Placed(TicketFacts facts, List<String> projects) implements Outcome {}

  public Answer<TicketFacts> read(String ref) {
    return tickets.read(ref);
  }

  public boolean namesAnItem(@Nullable String ref) {
    return workflow.isItemKey(ref);
  }

  /**
   * The item's own checks, placed in no project: the one a task that plans before it has a
   * repository takes.
   */
  public Outcome unplaced(String ref, Answer<TicketFacts> read) {
    // Three different answers: one names a missing item, the others a read that never got there.
    if (read.facts().isEmpty()) {
      String cause = read.failure().isBlank() ? "(cause in the log)" : "— " + read.failure();
      return new Refused("error: read failed: " + ref + " " + cause + " — no task created");
    }
    if (!read.facts().get().exists()) {
      return new Refused(
          "error: no such item: " + ref + " (the tracker says so) — no task created");
    }
    var facts = read.facts().filter(TicketFacts::usable);
    if (facts.isEmpty()) {
      return new Refused(
          "error: read incomplete: " + ref + " (no key, title or url) — no task created");
    }
    TicketFacts f = facts.get();
    if (workflow.isItemKey(ref) && !ref.equalsIgnoreCase(f.key())) {
      return new Refused(
          "error: asked for "
              + ref
              + " and got "
              + f.key()
              + " back — no task"
              + " created. Launch it under the key the tracker itself reports.");
    }
    return new Placed(f, List.of());
  }

  /** {@code chosen} is null where no human named a project. */
  public Outcome place(String ref, Answer<TicketFacts> read, @Nullable List<String> chosen) {
    return switch (unplaced(ref, read)) {
      case Refused refused -> refused;
      case Placed placed ->
          chosen != null ? new Placed(placed.facts(), chosen) : routed(placed.facts());
    };
  }

  /** A human who named a project has settled it; only an unplaced one is worth asking about. */
  private Outcome routed(TicketFacts f) {
    ProjectRouting.Placement placement = routing.projectFor(f);
    return placement.projects().isEmpty()
        ? new Refused(
            "error: "
                + f.key()
                + " not placed in a configured project: "
                + placement.reason()
                + " — say which: do "
                + f.key()
                + " <project>")
        : new Placed(f, placement.projects());
  }

  /** Only NOW does the task exist, so only now can the read that named it be charged to it. */
  public void created(String taskId, @Nullable List<String> chosen, TokenUsage usage) {
    // Only where the human named it: their word against a rule is the correction, and the router's
    // own
    // placement contradicts nothing.
    if (chosen != null) {
      routing.placedByHand(taskId, chosen.get(0));
    }
    tickets.charge(taskId, usage);
  }
}
