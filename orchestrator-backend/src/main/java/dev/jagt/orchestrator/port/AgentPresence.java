package dev.jagt.orchestrator.port;

/** Whether a task's agent is still working. Costs a probe, so a rule asks only where the answer changes it. */
public interface AgentPresence {

    boolean agentLive(String taskId);
}
