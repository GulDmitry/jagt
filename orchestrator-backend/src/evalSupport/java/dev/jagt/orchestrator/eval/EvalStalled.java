package dev.jagt.orchestrator.eval;

public class EvalStalled extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private EvalStalled(String message) {
    super(message);
  }

  public static EvalStalled because(String message) {
    return new EvalStalled(message);
  }
}
