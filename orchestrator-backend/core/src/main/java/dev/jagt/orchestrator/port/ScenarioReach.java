package dev.jagt.orchestrator.port;

/** Whether the acceptance session can open: its role is on and holds a workspace. */
@FunctionalInterface
public interface ScenarioReach {

  boolean ready();
}
