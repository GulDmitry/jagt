package dev.jagt.orchestrator.service;

/** A project's setup command failed in a fresh worktree: the task does not start. */
public class SetupFailed extends IllegalStateException {

  public SetupFailed(String message) {
    super(message);
  }
}
