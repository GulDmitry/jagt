package dev.jagt.orchestrator.port;

/** Whether the running agent CLI has a shell escape a line can be typed behind. */
@FunctionalInterface
public interface ShellEscapeReach {

  boolean ready();
}
