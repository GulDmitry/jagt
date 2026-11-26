package dev.jagt.orchestrator.task.turn;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Locale;

/** Who asked for a status change. */
public enum ActionOrigin {
  BOARD(false),
  /** Free text a model mapped to a command, whichever surface it was typed into. */
  PALETTE(false),
  /** An MCP call from a task's own session, reporting its progress. */
  MCP(false),
  /** The root session, holding the Master token, acting on a task it does not run. */
  ROOT(false),
  AUTO_REVIEW(true),
  /** The Master session acting where a human would have. */
  MASTER(true),
  /** The acceptance check sending the rows it did not meet back to the session. */
  @JsonAlias("QA")
  ACCEPTANCE(true),
  /** A stage the tracker itself reports, which is nobody's judgement and so nobody's to hold. */
  TRACKER(true),
  /**
   * jagt's own deterministic move on the code host: the re-run of a pipeline that died on
   * infrastructure.
   */
  JAGT(true),
  /** The approval of a review request, read from the code host. */
  CODE_HOST(true);

  private final boolean unattended;

  ActionOrigin(boolean unattended) {
    this.unattended = unattended;
  }

  /** Whether a move of this origin was made without a human asking for it. */
  public boolean unattended() {
    return unattended;
  }

  /** Whether a system outside jagt's own surfaces, not a human's press, opened the task. */
  public boolean outside() {
    return this == TRACKER || this == CODE_HOST;
  }

  public String label() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
