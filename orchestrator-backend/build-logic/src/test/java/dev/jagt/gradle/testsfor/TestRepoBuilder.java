package dev.jagt.gradle.testsfor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record TestRepoBuilder(Path root) {

  static TestRepoBuilder in(Path root) {
    return new TestRepoBuilder(root);
  }

  TestRepoBuilder file(String path, String content) throws IOException {
    Files.createDirectories(root.resolve(path).getParent());
    Files.writeString(root.resolve(path), content);
    return this;
  }

  TestsFor trackedAtTheTop() throws IOException, InterruptedException {
    new ProcessBuilder("git", "-C", root.toString(), "init", "-q").start().waitFor();
    new ProcessBuilder("git", "-C", root.toString(), "add", "-A").start().waitFor();
    return TestsFor.in(root);
  }

  TestsFor tracked() throws IOException, InterruptedException {
    Files.createDirectories(root.resolve("orchestrator-backend"));
    new ProcessBuilder("git", "-C", root.toString(), "init", "-q").start().waitFor();
    new ProcessBuilder("git", "-C", root.toString(), "add", "-A").start().waitFor();
    return TestsFor.in(root.resolve("orchestrator-backend"));
  }
}
