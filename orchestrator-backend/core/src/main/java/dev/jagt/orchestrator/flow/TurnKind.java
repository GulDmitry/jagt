package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.Turn;

/** The turn region's states: whether the session holds a relayed turn. */
enum TurnKind {
    CLOSED, OPEN;

    static TurnKind of(Turn turn) {
        return turn.open() ? OPEN : CLOSED;
    }
}
