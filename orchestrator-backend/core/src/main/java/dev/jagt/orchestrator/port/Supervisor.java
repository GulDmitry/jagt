package dev.jagt.orchestrator.port;

import java.nio.file.Path;

/** Whatever keeps jagt's staged jar running and starts it again on request. */
public interface Supervisor {

  /** What a call achieved; a refusal says what the human changes. */
  sealed interface Supervision {

    String sentence();

    record Done(String sentence) implements Supervision {}

    record Refused(String sentence) implements Supervision {}
  }

  /** False where nobody keeps jagt alive. */
  boolean supervised();

  /** Registers {@code stagedJar} to run, and to run again whenever it exits. */
  Supervision install(Path stagedJar);

  /** Ends the running jagt and starts the installed jar in its place. */
  Supervision restart();
}
