package dev.jagt.orchestrator;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class SourceRoots {

  private static final Path BACKEND = Path.of(".");
  private static final int DEEPEST = 4;

  static final List<Path> ROOTS = roots();

  static final JavaClasses ALL = new ClassFileImporter().importPaths(classDirectories());

  static final ClassLoader LOADER = loader();

  private SourceRoots() {}

  static Stream<Path> mainJavaFiles() {
    return ROOTS.stream().filter(root -> !isTest(root)).flatMap(SourceRoots::javaFilesUnder);
  }

  static Stream<Path> javaFiles() {
    return ROOTS.stream().flatMap(SourceRoots::javaFilesUnder);
  }

  static Stream<Path> javaFilesUnder(Path root) {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java")).toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Path moduleOf(Path path) {
    return path.getName(0).toString().equals("src") ? Path.of(".") : path.getName(0);
  }

  static boolean isTest(Path root) {
    return !setOf(root).equals("main");
  }

  private static String setOf(Path root) {
    return root.getName(root.getNameCount() - 2).toString();
  }

  private static List<Path> roots() {
    try (Stream<Path> tree = Files.walk(BACKEND, DEEPEST)) {
      return tree.filter(Files::isDirectory)
          .filter(path -> path.getFileName().toString().equals("java"))
          .filter(path -> path.getNameCount() >= 3)
          .filter(path -> path.getName(path.getNameCount() - 3).toString().equals("src"))
          .filter(path -> !path.startsWith(BACKEND.resolve("build-logic")))
          .map(BACKEND::relativize)
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Path> classDirectories() {
    return roots().stream()
        .map(
            root ->
                moduleOf(root)
                    .resolve("build/classes/java")
                    .resolve(setOf(root)))
        .filter(Files::isDirectory)
        .toList();
  }

  private static ClassLoader loader() {
    URL[] urls =
        classDirectories().stream()
            .map(directory -> directory.toAbsolutePath().normalize())
            .map(
                directory -> {
                  try {
                    return directory.toUri().toURL();
                  } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, SourceRoots.class.getClassLoader());
  }
}
