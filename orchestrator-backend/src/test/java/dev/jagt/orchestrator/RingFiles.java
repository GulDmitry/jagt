package dev.jagt.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class RingFiles {

  static final List<Path> ROOTS =
      SourceRoots.ROOTS.stream()
          .map(root -> root.resolve("dev/jagt/orchestrator"))
          .filter(Files::isDirectory)
          .toList();

  private RingFiles() {}

  static String packageOf(String ring) {
    return "dev.jagt.orchestrator." + ring + "..";
  }

  static Stream<Path> outsideTheEdge(boolean withTests) {
    return SourceRoots.ROOTS.stream()
        .filter(root -> withTests || !SourceRoots.isTest(root))
        .filter(root -> !SourceRoots.moduleOf(root).equals(Path.of("adapter")))
        .filter(
            root -> !(SourceRoots.isTest(root) && SourceRoots.moduleOf(root).equals(Path.of("."))))
        .flatMap(SourceRoots::javaFilesUnder)
        .filter(path -> !path.toString().contains("/adapter/"));
  }

  static Stream<String> sources(String ring) {
    return ROOTS.stream()
        .map(root -> root.resolve(ring))
        .filter(Files::isDirectory)
        .flatMap(SourceRoots::javaFilesUnder)
        .map(SourceRoots::read);
  }

  static Stream<Path> inModule(String module, boolean mainOnly) {
    return SourceRoots.ROOTS.stream()
        .filter(root -> SourceRoots.moduleOf(root).equals(Path.of(module)))
        .filter(root -> !mainOnly || !SourceRoots.isTest(root))
        .flatMap(SourceRoots::javaFilesUnder);
  }
}
