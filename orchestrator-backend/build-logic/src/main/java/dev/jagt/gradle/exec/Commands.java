package dev.jagt.gradle.exec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.gradle.process.ExecOperations;

/** Runs a command to completion in a directory. */
public final class Commands {

  private Commands() {}

  /** The standard output of a command that exited 0, empty when it exited otherwise. */
  public static Optional<String> output(
      ExecOperations operations, Path directory, String... command) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exit =
        operations
            .exec(
                spec -> {
                  spec.setWorkingDir(directory.toFile());
                  spec.commandLine(List.of(command));
                  spec.setIgnoreExitValue(true);
                  spec.setStandardOutput(output);
                  spec.setErrorOutput(new ByteArrayOutputStream());
                })
            .getExitValue();
    return exit == 0 ? Optional.of(output.toString(StandardCharsets.UTF_8)) : Optional.empty();
  }
}
