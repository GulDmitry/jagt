package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.codehost.RequestlessShip;
import dev.jagt.orchestrator.task.TaskState;

/** What a task's facts read from outside its stored state: its agent's window, its ship. */
public record FactSources(AgentPresence agents, RequestlessShip requestless) {

  boolean agentLive(String taskId) {
    try {
      return agents.agentLive(taskId);
    } catch (AgentPresence.Unread unread) {
      throw Refusal.byState(
          "Cannot tell whether the session runs, its window was not read: " + unread.getMessage());
    }
  }

  boolean shippedWithoutRequest(TaskState task) {
    return requestless.stands(task);
  }
}
