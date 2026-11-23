package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import lombok.With;

@With
public record TestCarriedBlockBuilder() {

  public static TestCarriedBlockBuilder carriedBlock() {
    return new TestCarriedBlockBuilder();
  }

  public CarriedBlock build(WorktreeChanges changes, AgentRuntime runtime) {
    return new CarriedBlock(changes, runtime);
  }
}
