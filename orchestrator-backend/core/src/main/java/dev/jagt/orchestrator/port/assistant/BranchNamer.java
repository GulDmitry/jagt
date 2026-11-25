package dev.jagt.orchestrator.port.assistant;

/** The few words naming what a written task does. Every call is booked. */
public interface BranchNamer {

  /** Reads NOTHING from the outside, so an implementation runs with no tools at all. */
  Answer<String> nameBranch(String prompt);
}
