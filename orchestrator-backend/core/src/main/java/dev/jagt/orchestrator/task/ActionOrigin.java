package dev.jagt.orchestrator.task;

import java.util.Locale;

/** Who asked for a status change. */
public enum ActionOrigin {
  BOARD,
  /** Free text a model mapped to a command, whichever surface it was typed into. */
  PALETTE,
  /** An MCP call from a task's own session, reporting its progress. */
  MCP,
  /** The root session, holding the Master token, acting on a task it does not run. */
  ROOT,
  AUTO_REVIEW,
  /** The Master session acting where a human would have. */
  MASTER,
  /** The QA check sending the rows it did not meet back to the session. */
  QA,
  /** A stage the tracker itself reports, which is nobody's judgement and so nobody's to hold. */
  TRACKER,
  /**
   * jagt's own deterministic move on the code host: the re-run of a pipeline that died on
   * infrastructure.
   */
  JAGT;

  public String label() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
