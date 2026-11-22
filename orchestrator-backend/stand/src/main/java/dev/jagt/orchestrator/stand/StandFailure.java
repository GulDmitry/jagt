package dev.jagt.orchestrator.stand;

public class StandFailure extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StandFailure(String cause) {
    super(cause);
  }

  public StandFailure(String cause, Throwable underlying) {
    super(cause, underlying);
  }
}
