package dev.jagt.orchestrator.port.assistant;

import java.util.List;

/**
 * Free text read with no tools: mapped onto ONE command of the console grammar — a PROPOSAL, never
 * an execution — or named in the few words of a branch. Every call is booked.
 */
public interface CommandAssistant {

  /**
   * {@code command} empty (or "none") means nothing matched, and {@code reason} then says why in
   * one line.
   */
  record CommandProposal(String command, String task, String ticket, String reason) {}

  /**
   * What may be answered: {@code prompt} lists it for the model, the two lists are the same words
   * for decoding.
   */
  record Menu(String prompt, List<String> commands, List<String> tasks) {}

  /** Reads NOTHING from the outside, so an implementation runs with no tools at all. */
  Answer<CommandProposal> mapCommand(String text, Menu menu);

  /** The few words naming what a written task does; reads NOTHING from the outside either. */
  Answer<String> nameBranch(String prompt);
}
