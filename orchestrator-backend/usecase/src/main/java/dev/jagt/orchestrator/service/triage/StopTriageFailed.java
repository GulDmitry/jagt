package dev.jagt.orchestrator.service.triage;

public final class StopTriageFailed extends RuntimeException {

  private final String why;

  public StopTriageFailed(String why) {
    super(why);
    this.why = why;
  }

  public String why() {
    return why;
  }
}
