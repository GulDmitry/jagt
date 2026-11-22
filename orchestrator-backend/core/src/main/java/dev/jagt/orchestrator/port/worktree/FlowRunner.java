package dev.jagt.orchestrator.port.worktree;

import java.nio.file.Path;
import java.util.List;
import lombok.With;

/**
 * Runs scripted flows against one environment and answers per flow what the run proved. A flow that
 * could not be run is {@link Verdict#UNPROVABLE}, never {@link Verdict#FAILED}.
 */
public interface FlowRunner {

  enum Verdict {
    PASSED,
    /** The flow ran and did not hold: evidence for "not met". */
    FAILED,
    /** The flow could not be judged: not run, not found, or unstable. */
    UNPROVABLE
  }

  /** {@code evidence} lies under the worktree's {@code acceptance/} folder. */
  @With
  record FlowResult(String flow, Verdict verdict, String reason, List<Path> evidence) {

    public FlowResult {
      evidence = List.copyOf(evidence);
    }

    public static FlowResult defaults() {
      return new FlowResult("", Verdict.UNPROVABLE, "", List.of());
    }
  }

  /**
   * One result per flow, in the order given, run as the folder of the checker {@code role} says.
   */
  List<FlowResult> run(String role, Path worktree, List<String> flows, String environment);
}
