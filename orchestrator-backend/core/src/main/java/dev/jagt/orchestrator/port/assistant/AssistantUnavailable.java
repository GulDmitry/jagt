package dev.jagt.orchestrator.port.assistant;

/** A read refused unasked because the assistant's provider failed repeatedly; never "not found". */
public final class AssistantUnavailable extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  private final String lastCause;

  public AssistantUnavailable(String lastCause) {
    super("assistant unavailable: " + lastCause);
    this.lastCause = lastCause;
  }

  public String lastCause() {
    return lastCause;
  }
}
