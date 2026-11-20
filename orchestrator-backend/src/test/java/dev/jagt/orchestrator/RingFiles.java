package dev.jagt.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class RingFiles {

  public static final List<Path> ROOTS =
      SourceRoots.ROOTS.stream()
          .map(root -> root.resolve(SourceRoots.BASE_PATH))
          .filter(Files::isDirectory)
          .toList();

  private RingFiles() {}

  public static String packageOf(String ring) {
    return SourceRoots.BASE_PACKAGE + "." + ring + "..";
  }

  public static Stream<Path> outsideTheEdge(boolean withTests) {
    return SourceRoots.ROOTS.stream()
        .filter(root -> withTests || !SourceRoots.isFixture(root))
        .filter(root -> !SourceRoots.moduleOf(root).equals(Path.of("adapter")))
        .filter(
            root -> !(SourceRoots.isTest(root) && SourceRoots.moduleOf(root).equals(Path.of("."))))
        .flatMap(SourceRoots::javaFilesUnder)
        .filter(path -> !path.toString().contains("/adapter/"));
  }

  public static Stream<String> sources(String ring) {
    return ROOTS.stream()
        .map(root -> root.resolve(ring))
        .filter(Files::isDirectory)
        .flatMap(SourceRoots::javaFilesUnder)
        .map(SourceRoots::read);
  }

  public static Stream<Path> inModule(String module, boolean mainOnly) {
    return SourceRoots.ROOTS.stream()
        .filter(root -> SourceRoots.moduleOf(root).equals(Path.of(module)))
        .filter(root -> !mainOnly || !SourceRoots.isTest(root))
        .flatMap(SourceRoots::javaFilesUnder);
  }
}
