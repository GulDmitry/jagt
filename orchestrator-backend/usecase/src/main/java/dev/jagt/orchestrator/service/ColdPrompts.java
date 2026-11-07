package dev.jagt.orchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A prompt typed into a session gone cold, handed to a fresh one that carries what the old one
 * knew.
 */
@Component
@RequiredArgsConstructor
public class ColdPrompts {

  private final AgentSessions sessions;

  /**
   * The prompt is on disk when this returns and the fresh window launched or waiting; a failure is
   * thrown.
   */
  public void relay(String taskId, String prompt) {
    sessions.relayUnderCold(taskId, prompt);
  }
}
