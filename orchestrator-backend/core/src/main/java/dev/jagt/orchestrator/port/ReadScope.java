package dev.jagt.orchestrator.port;

/** The span of one job run, within which what the host's windows and processes say is read once. */
public interface ReadScope {

  /**
   * Runs {@code tick}, during which the windows and processes it asks about are listed once for
   * every task it visits. A window opened, closed or pressed makes the next ask list again; nothing
   * outlives the call.
   */
  void once(Runnable tick);
}
