package dev.jagt.orchestrator.flow;

import java.util.List;

/** A piece of work in flight that jagt waits on; derived from the stored facts, never stored. */
sealed interface ProgressStep {

  List<ProgressStep> ALL =
      List.of(
          new SessionTurn(),
          new Verifying(),
          new HostRead(),
          new InfraRerun(),
          new JudgeRound(),
          new Deploy());

  record SessionTurn() implements ProgressStep {}

  record Verifying() implements ProgressStep {}

  record HostRead() implements ProgressStep {}

  record InfraRerun() implements ProgressStep {}

  record JudgeRound() implements ProgressStep {}

  /** In flight until the post-merge pipeline reads green; the session may not leave. */
  record Deploy() implements ProgressStep {}
}
