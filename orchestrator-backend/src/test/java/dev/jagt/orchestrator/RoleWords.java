package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RoleWords {

  private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
  private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");

  private RoleWords() {}

  public static List<String> inSurface(List<String> ids) {
    return inJava(Path.of("surface", "src", "main", "java"), ids, List.of());
  }

  public static List<String> inCoreAndUsecase(List<String> ids) {
    return Stream.of("core", "usecase")
        .flatMap(
            module -> inJava(Path.of(module, "src", "main", "java"), ids, vocabulary()).stream())
        .toList();
  }

  private static List<String> vocabulary() {
    return Stream.of(RoleModules.STOP_KIND, RoleModules.CHECKER_KIND, RoleModules.STAND_IN_KIND)
        .flatMap(List::stream)
        .toList();
  }

  private static List<String> inJava(Path root, List<String> ids, List<String> vocabulary) {
    List<String> modules =
        Stream.of(
                RoleModules.ARBITER.stream(),
                RoleModules.CHECKERS.values().stream().flatMap(List::stream),
                vocabulary.stream())
            .flatMap(names -> names)
            .toList();
    return files(root, ".java")
        .filter(file -> modules.stream().noneMatch(classOf(root, file)::startsWith))
        .flatMap(file -> found(file, ids, true))
        .toList();
  }

  public static List<String> inBoard(List<String> ids) {
    return files(Path.of("..", "board", "ui"), ".js", ".html")
        .flatMap(file -> found(file, ids, false))
        .toList();
  }

  private static String classOf(Path root, Path file) {
    return root.relativize(file).toString().replace('/', '.').replaceAll("\\.java$", "");
  }

  private static Stream<String> found(Path file, List<String> ids, boolean java) {
    String text = read(file);
    String code = java ? COMMENTS.matcher(text).replaceAll("") : text;
    String words =
        CAMEL.matcher(code).replaceAll("$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
    return ids.stream()
        .filter(id -> Pattern.compile("\\b" + Pattern.quote(id) + "\\b").matcher(words).find())
        .map(id -> file.toString().replaceFirst("^\\.\\./", "") + " " + id);
  }

  private static Stream<Path> files(Path root, String... endings) {
    try (Stream<Path> walked = Files.walk(root)) {
      return walked
          .filter(path -> Stream.of(endings).anyMatch(path.toString()::endsWith))
          .toList()
          .stream();
    } catch (IOException unread) {
      throw new UncheckedIOException(unread);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException unread) {
      throw new UncheckedIOException(unread);
    }
  }
}
