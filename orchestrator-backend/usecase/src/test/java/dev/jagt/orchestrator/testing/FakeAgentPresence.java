package dev.jagt.orchestrator.testing;

import dev.jagt.orchestrator.port.AgentPresence;

public final class FakeAgentPresence implements AgentPresence {

  @Override
  public boolean agentLive(String taskId) {
    return true;
  }
}
