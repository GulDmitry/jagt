package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.TestCarriedBlockBuilder.carriedBlock;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import lombok.With;

@With
public record TestSessionHostingBuilder() {

  public static TestSessionHostingBuilder sessionHosting() {
    return new TestSessionHostingBuilder();
  }

  public SessionHosting build(SessionHost host, AgentRuntime runtime, WorktreeChanges changes) {
    return new SessionHosting(host, runtime, carriedBlock().build(changes, runtime));
  }
}
