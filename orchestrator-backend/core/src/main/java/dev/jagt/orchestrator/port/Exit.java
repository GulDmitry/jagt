package dev.jagt.orchestrator.port;

/** Ends this jagt once the answer being given has been sent. */
public interface Exit {

  void afterAnswer();
}
