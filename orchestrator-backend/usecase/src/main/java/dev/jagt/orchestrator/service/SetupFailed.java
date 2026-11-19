package dev.jagt.orchestrator.service;

/** A project's setup command failed in a fresh worktree: the task does not start. */
public class SetupFailed extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public SetupFailed(String message) {
    super(message);
  }
}
