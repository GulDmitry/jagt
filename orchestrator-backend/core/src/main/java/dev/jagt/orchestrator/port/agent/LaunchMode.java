package dev.jagt.orchestrator.port.agent;

/** What a launched agent session starts on: a plan to approve first, or the work itself. */
public enum LaunchMode {
  PLAN,
  WORK
}
