package dev.jagt.gradle.hotspots;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

record TestHistoryBuilder(Path root) {

  static TestHistoryBuilder in(Path root) throws IOException, InterruptedException {
    run(root, Instant.EPOCH, "init", "-q");
    return new TestHistoryBuilder(root);
  }

  TestHistoryBuilder commit(String path, Instant at, String message)
      throws IOException, InterruptedException {
    Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "x\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    run(root, at, "add", path);
    run(root, at, "commit", "-q", "-m", message);
    return this;
  }

  TestHistoryBuilder shallowCloneTo(Path target) throws IOException, InterruptedException {
    run(root, Instant.EPOCH, "clone", "-q", "--depth", "1", "file://" + root, target.toString());
    return new TestHistoryBuilder(target);
  }

  private static void run(Path root, Instant at, String... arguments)
      throws IOException, InterruptedException {
    List<String> command =
        new java.util.ArrayList<>(
            List.of("git", "-C", root.toString(), "-c", "user.name=t", "-c", "user.email=t@t.t"));
    command.addAll(List.of(arguments));
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    builder.environment().put("GIT_COMMITTER_DATE", at.toString());
    builder.environment().put("GIT_AUTHOR_DATE", at.toString());
    Process process = builder.start();
    process.getInputStream().readAllBytes();
    process.waitFor();
  }
}
