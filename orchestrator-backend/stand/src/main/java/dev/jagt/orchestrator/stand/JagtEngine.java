package dev.jagt.orchestrator.stand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface JagtEngine extends AutoCloseable {

  long pid();

  int port();

  StandBoard board();

  /** The status of a request from a peer that is not loopback to jagt, however jagt is reached. */
  default int fromOutside(String path, Map<String, String> headers, @Nullable String body) {
    return board().anonymously(path, headers, body);
  }

  String windows();

  String screens();

  /** Jagt's data folder as jagt itself sees it. */
  Path data();

  /** A program run beside jagt, where jagt runs. */
  StandShell.Result exec(String... command);

  /** Everything jagt printed since the engine started. */
  String log();

  JagtEngine restart(Restart how);

  /** Sends jagt SIGTERM and returns once it has exited and nothing revives it. */
  void terminate();

  /** The task state jagt left in its data folder, read without jagt. */
  default String state() {
    Path file = data().resolve("jagt").resolve("state.json");
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new StandFailure("cannot read " + file, e);
    }
  }

  default void logs() {}

  /** Brings what the host reads of jagt's data folder into the run's root. */
  default void harvest() {}

  @Override
  void close();
}
