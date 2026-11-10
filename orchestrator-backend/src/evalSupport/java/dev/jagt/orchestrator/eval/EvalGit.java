package dev.jagt.orchestrator.eval;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class EvalGit {

  private EvalGit() {}

  public static void run(Path directory, String... arguments) throws Exception {
    String[] command = new String[arguments.length + 1];
    command[0] = "git";
    System.arraycopy(arguments, 0, command, 1, arguments.length);
    Process process =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
      throw new IllegalStateException(
          "git "
              + String.join(" ", arguments)
              + " failed in "
              + directory
              + ": "
              + new String(process.getInputStream().readAllBytes(), UTF_8));
    }
  }
}
