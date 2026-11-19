package dev.jagt.orchestrator.surface.mcp.refusal;

/** A call jagt turned away before any work, knowing exactly why. */
public class ToolRefusal extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final ToolFailure failure;

  public ToolRefusal(ToolFailure failure, String message) {
    super(message);
    this.failure = failure;
  }

  public ToolFailure failure() {
    return failure;
  }
}
