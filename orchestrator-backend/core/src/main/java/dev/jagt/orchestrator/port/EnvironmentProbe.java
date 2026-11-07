package dev.jagt.orchestrator.port;

/**
 * Whether an environment is up: a plain GET of its address answered with anything but a server
 * fault.
 */
public interface EnvironmentProbe {

  boolean answers(String url);
}
