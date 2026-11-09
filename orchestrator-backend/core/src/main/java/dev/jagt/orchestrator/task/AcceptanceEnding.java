package dev.jagt.orchestrator.task;

/** How a check ended, each named in the record with the sha it checked. */
public enum AcceptanceEnding {
  PASSED,
  NOT_MET,
  UNPROVABLE,
  WAITING,
  DECLINED,
  DISPUTED,
  ESCALATED,
  DROPPED,
  FAILED,
  GONE
}
