package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class BuildCeilings {

  private static final Path BACKEND = Path.of(".");
  private static final Path ROOT = Path.of("..");
  private static final Pattern INCLUDE = Pattern.compile("^include\\s+'([^']+)'");
  private static final Pattern DEPENDENCY =
      Pattern.compile(
          "^\\s+([a-z][A-Za-z]*)?" + "([Aa]pi|[Ii]mplementation|[Cc]ompileOnly|[Rr]untimeOnly)\\s");

  private BuildCeilings() {}

  static List<String> gradleModules() {
    return lines(BACKEND.resolve("settings.gradle"))
        .map(INCLUDE::matcher)
        .filter(Matcher::find)
        .map(match -> match.group(1))
        .sorted()
        .toList();
  }

  static List<String> buildLogic() {
    return files(BACKEND.resolve("build-logic"))
        .filter(path -> path.toString().endsWith(".gradle") || path.toString().endsWith(".groovy"))
        .map(path -> BACKEND.relativize(path).toString())
        .sorted()
        .toList();
  }

  static List<String> dependencyLinesInBuildGradle() {
    return files(BACKEND)
        .filter(path -> path.getFileName().toString().equals("build.gradle"))
        .flatMap(path ->
            lines(path)
                .filter(line -> DEPENDENCY.matcher(line).find())
                .map(line -> BACKEND.relativize(path) + " " + line.trim()))
        .sorted()
        .toList();
  }

  static List<String> roles() {
    return directories(ROOT.resolve("roles"));
  }

  static List<String> specCapabilities() {
    return directories(ROOT.resolve("openspec/specs"));
  }

  static List<String> ruleDocs() {
    try (Stream<Path> docs = Files.list(ROOT.resolve("docs/rules"))) {
      return docs.map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".md"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> directories(Path parent) {
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Stream<Path> files(Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk
          .filter(Files::isRegularFile)
          .filter(path -> !isBuildOutput(root.relativize(path)))
          .toList()
          .stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean isBuildOutput(Path relative) {
    for (Path part : relative) {
      if (List.of("build", "bin", ".gradle").contains(part.toString())) {
        return true;
      }
    }
    return false;
  }

  private static Stream<String> lines(Path file) {
    try {
      return Files.readAllLines(file).stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
