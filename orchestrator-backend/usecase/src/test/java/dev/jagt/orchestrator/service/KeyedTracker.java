package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TicketFacts;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class KeyedTracker implements TrackerWorkflow {

  private final Set<String> keys;

  public KeyedTracker(String... keys) {
    this.keys = Set.of(keys);
  }

  @Override
  public String id() {
    return "keyed";
  }

  @Override
  public boolean isItemKey(@Nullable String name) {
    return keys.contains(name);
  }

  @Override
  public String candidateQuery() {
    return "";
  }

  @Override
  public boolean startsWork(TicketFacts facts) {
    return false;
  }

  @Override
  public boolean closesWork(TicketFacts facts) {
    return false;
  }
}
