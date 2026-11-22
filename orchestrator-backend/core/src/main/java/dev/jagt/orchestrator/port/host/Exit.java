package dev.jagt.orchestrator.port.host;

/** Ends this jagt once the answer being given has been sent. */
public interface Exit {

  /**
   * The exit code meaning "run me again"; the launcher starts the jar its {@code current} names.
   */
  int RUN_AGAIN = 75;

  /** Set by the launcher that restarts jagt on {@link #RUN_AGAIN}. */
  String LAUNCHER_ENV = "JAGT_LAUNCHER";

  void afterAnswer(int code);
}
