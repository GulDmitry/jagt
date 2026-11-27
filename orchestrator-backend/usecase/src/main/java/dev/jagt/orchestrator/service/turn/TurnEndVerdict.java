package dev.jagt.orchestrator.service.turn;

/**
 * What a turn end is answered with: nothing, a note for the human, or a refusal sent to the agent.
 */
public sealed interface TurnEndVerdict {

  /** The turn ends and nothing is said. */
  record Silent() implements TurnEndVerdict {}

  /** The turn ends and the human is told {@code note}. */
  record Tell(String note) implements TurnEndVerdict {}

  /** The turn is sent on with {@code reason}. */
  record Refuse(String reason) implements TurnEndVerdict {}
}
