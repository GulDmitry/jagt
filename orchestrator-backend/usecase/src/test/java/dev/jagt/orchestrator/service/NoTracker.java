package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TicketFacts;
import org.jspecify.annotations.Nullable;

public final class NoTracker implements TrackerWorkflow {

  @Override
  public String id() {
    return "none";
  }

  @Override
  public boolean isItemKey(@Nullable String name) {
    return false;
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
