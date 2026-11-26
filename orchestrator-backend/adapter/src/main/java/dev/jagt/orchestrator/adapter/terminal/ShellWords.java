package dev.jagt.orchestrator.adapter.terminal;

import java.util.List;
import java.util.stream.Collectors;

/** Words quoted so a POSIX shell reads each back as one argument. */
public final class ShellWords {

  private ShellWords() {}

  public static String quoted(String word) {
    return "'" + word.replace("'", "'\\''") + "'";
  }

  public static String joined(List<String> words) {
    return words.stream().map(ShellWords::quoted).collect(Collectors.joining(" "));
  }
}
