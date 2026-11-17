package dev.jagt.orchestrator.stand;

import java.nio.file.Path;

public interface JagtEngine extends AutoCloseable {

  long pid();

  int port();

  StandBoard board();

  String windows();

  String screens();

  /** Jagt's data folder as jagt itself sees it. */
  Path data();

  /** A program run beside jagt, where jagt runs. */
  StandShell.Result exec(String... command);

  /** Everything jagt printed since the engine started. */
  String log();

  JagtEngine restart(Restart how);

  default void logs() {}

  @Override
  void close();
}
