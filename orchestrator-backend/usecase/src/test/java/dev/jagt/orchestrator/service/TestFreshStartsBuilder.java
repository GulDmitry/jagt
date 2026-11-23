package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.session.SessionHosting;
import lombok.With;

@With
public record TestFreshStartsBuilder() {

  public static TestFreshStartsBuilder freshStarts() {
    return new TestFreshStartsBuilder();
  }

  public FreshStarts build(AgentWindows windows, StateService state, SessionHosting hosting) {
    return new FreshStarts(windows, state, hosting);
  }
}
