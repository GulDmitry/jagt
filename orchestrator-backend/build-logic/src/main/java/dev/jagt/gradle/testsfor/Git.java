package dev.jagt.gradle.testsfor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

final class Git {

  private final Path directory;

  Git(Path directory) {
    this.directory = directory;
  }

  List<String> lines(String... arguments) {
    return split(run(arguments), "\r?\n");
  }

  List<String> nulSeparated(String... arguments) {
    return split(run(arguments), "\0");
  }

  private static List<String> split(String output, String separator) {
    return Stream.of(output.split(separator)).filter(entry -> !entry.isEmpty()).toList();
  }

  private String run(String... arguments) {
    List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
    command.addAll(Arrays.asList(arguments));
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
      byte[] error = process.getErrorStream().readAllBytes();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (exit != 0) {
        throw new IllegalStateException(
            "git failed, exit "
                + exit
                + ": "
                + String.join(" ", command)
                + ": "
                + new String(error, StandardCharsets.UTF_8).strip());
      }
      return output;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted running git in " + directory, e);
    }
  }
}
