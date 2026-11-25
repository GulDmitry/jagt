package dev.jagt.gradle.testsfor;

import dev.jagt.gradle.exec.Git;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

record Repo(Git git, Path top) {

  static Repo around(Path directory) {
    List<String> top = new Git(directory).lines("rev-parse", "--show-toplevel");
    if (top.isEmpty()) {
      throw new IllegalStateException("not inside a git repository: " + directory);
    }
    Path root = Path.of(top.get(0));
    return new Repo(new Git(root), root);
  }

  List<String> changed(String base) {
    return new TreeSet<>(
            Stream.of(
                    git.nulSeparated("diff", "-z", "--name-only", "--no-renames", base + "...HEAD"),
                    git.nulSeparated("diff", "-z", "--name-only", "--no-renames", "HEAD"),
                    git.nulSeparated("ls-files", "-z", "--others", "--exclude-standard"))
                .flatMap(List::stream)
                .toList())
        .stream().toList();
  }

  boolean exists(String path) {
    return Files.exists(top.resolve(path));
  }

  boolean tracked(String path) {
    return !git.lines("ls-files", "--", path).isEmpty();
  }

  List<String> trackedFiles(String directory) {
    return git.lines("ls-files", "--", directory);
  }

  List<String> filesIn(List<String> directories) {
    return git.lines(
        Stream.concat(
                Stream.of("ls-files", "--cached", "--others", "--exclude-standard", "--"),
                directories.stream())
            .toArray(String[]::new));
  }

  boolean holds(String file, Predicate<String> holds) {
    return Files.isRegularFile(top.resolve(file)) && holds.test(read(top.resolve(file)));
  }

  List<String> filesWhoseText(Predicate<String> holds, List<String> directories) {
    return filesIn(directories).stream()
        .filter(file -> Files.isRegularFile(top.resolve(file)))
        .filter(file -> holds.test(read(top.resolve(file))))
        .toList();
  }

  private static String read(Path file) {
    try {
      return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
