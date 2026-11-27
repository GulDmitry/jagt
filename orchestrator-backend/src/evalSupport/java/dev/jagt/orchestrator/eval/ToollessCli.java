package dev.jagt.orchestrator.eval;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** One prompt through the claude CLI with no tool, answered by the text of its result. */
public record ToollessCli(String model) {

  public String answer(String prompt) throws IOException, InterruptedException {
    Path home = Files.createTempDirectory("toolless-read-");
    Path out = home.resolve("answer.json");
    try {
      Process process =
          new ProcessBuilder(
                  List.of(
                      "claude",
                      "-p",
                      "--model",
                      model,
                      "--output-format",
                      "json",
                      "--tools",
                      "",
                      "--no-session-persistence",
                      "--disable-slash-commands"))
              .directory(home.toFile())
              .redirectInput(Files.writeString(home.resolve("prompt.md"), prompt, UTF_8).toFile())
              .redirectOutput(out.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        process.destroyForcibly();
        throw EvalStalled.because("the reader gave no answer in ten minutes");
      }
      if (process.exitValue() != 0) {
        throw EvalStalled.because("the reader exited " + process.exitValue());
      }
      Map<String, Object> envelope = Yamls.load(Files.readString(out, UTF_8));
      return Yamls.text(envelope.get("result"));
    } finally {
      deleteAll(home);
    }
  }

  private static void deleteAll(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }
}
