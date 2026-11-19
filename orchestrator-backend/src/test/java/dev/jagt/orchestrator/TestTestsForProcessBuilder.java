package dev.jagt.orchestrator;

import java.nio.file.Path;

public record TestTestsForProcessBuilder(Path repo) {

  public static TestTestsForProcessBuilder over(Path repo) {
    return new TestTestsForProcessBuilder(repo);
  }

  public ProcessBuilder build(String changedPaths) {
    ProcessBuilder run =
        new ProcessBuilder(
            "sh",
            "-c",
            "git init -q . && git add -A && bash \"$0\" $1",
            Path.of("scripts/tests-for.sh").toAbsolutePath().toString(),
            changedPaths);
    run.directory(repo.toFile());
    run.redirectErrorStream(true);
    return run;
  }
}
