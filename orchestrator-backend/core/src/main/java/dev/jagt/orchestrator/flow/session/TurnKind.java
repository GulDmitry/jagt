package dev.jagt.orchestrator.flow.session;

import dev.jagt.orchestrator.task.turn.Turn;

/** The turn region's states: whether the session holds a relayed turn. */
public enum TurnKind {
  CLOSED,
  OPEN;

  public static TurnKind of(Turn turn) {
    return turn.open() ? OPEN : CLOSED;
  }
}
