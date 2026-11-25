package dev.jagt.gradle.exec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class Git {

  private final Path directory;

  public Git(Path directory) {
    this.directory = directory;
  }

  public List<String> lines(String... arguments) {
    return split(run(arguments), "\r?\n");
  }

  public List<String> nulSeparated(String... arguments) {
    return split(run(arguments), "\0");
  }

  private static List<String> split(String output, String separator) {
    return Stream.of(output.split(separator)).filter(entry -> !entry.isEmpty()).toList();
  }

  private String run(String... arguments) {
    List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
    command.addAll(Arrays.asList(arguments));
    try {
      Path errorFile = Files.createTempFile("git-error", ".txt");
      Process process = new ProcessBuilder(command).redirectError(errorFile.toFile()).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      byte[] error = Files.readAllBytes(errorFile);
      Files.delete(errorFile);
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
