package dev.jagt.orchestrator.service;

/** A project's setup command failed in a fresh worktree: the task does not start. */
public class SetupFailed extends RuntimeException {

  public SetupFailed(String message) {
    super(message);
  }
}
