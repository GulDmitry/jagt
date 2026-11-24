package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.session.SessionHosting;
import lombok.With;

@With
public record TestAgentWindowsBuilder() {

  public static TestAgentWindowsBuilder agentWindows() {
    return new TestAgentWindowsBuilder();
  }

  public AgentWindows build(ConfigService config, StateService state, SessionHosting hosting) {
    return new AgentWindows(config, state, hosting, unvetted -> null);
  }
}
