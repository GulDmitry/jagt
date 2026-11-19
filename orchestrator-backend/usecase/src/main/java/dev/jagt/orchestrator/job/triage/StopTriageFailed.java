package dev.jagt.orchestrator.job.triage;

public final class StopTriageFailed extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String why;

  public StopTriageFailed(String why) {
    super(why);
    this.why = why;
  }

  public String why() {
    return why;
  }
}
