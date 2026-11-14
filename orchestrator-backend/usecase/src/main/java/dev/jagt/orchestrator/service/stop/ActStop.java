package dev.jagt.orchestrator.service.stop;

/** Why act reads as judge. */
public record ActStop(Cause cause) {

  public enum Cause {
    STRIKES
  }
}
