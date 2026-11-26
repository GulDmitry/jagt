package dev.jagt.gradle.testsfor;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Layout(String backend) {

  static final String ROOT = "root";
  static final List<String> MODULES =
      List.of("statechart", "core", "usecase", "adapter", "surface", "stand", "mutation-scope");
  static final List<String> EVERY_UNIT = Stream.concat(MODULES.stream(), Stream.of(ROOT)).toList();

  record Located(String module, String set) {}

  List<String> dependents(String module) {
    return switch (module) {
      case "statechart" -> List.of("core", "usecase", "adapter", "surface", "stand", ROOT);
      case "core" -> List.of("usecase", "adapter", "surface", "stand", ROOT);
      case "usecase" -> List.of("adapter", "surface", "stand", ROOT);
      case "adapter" -> List.of("stand", ROOT);
      case "surface" -> List.of(ROOT);
      default -> List.of();
    };
  }

  String prefix(String module) {
    return module.equals(ROOT) ? "" : ":" + module;
  }

  String testDirectory(String module) {
    return module.equals(ROOT)
        ? within() + "src/test/java"
        : within() + module + "/src/test/java";
  }

  String directory() {
    return backend.isEmpty() ? "." : backend;
  }

  private String within() {
    return backend.isEmpty() ? "" : backend + "/";
  }

  Optional<String> insideBackend(String path) {
    return path.startsWith(within())
        ? Optional.of(path.substring(within().length()))
        : Optional.empty();
  }

  Optional<Located> locate(String path) {
    String inRoot = within() + "src/";
    if (path.startsWith(inRoot) && path.indexOf('/', inRoot.length()) >= 0) {
      String rest = path.substring(inRoot.length());
      return Optional.of(new Located(ROOT, rest.substring(0, rest.indexOf('/'))));
    }
    return inModule(path);
  }

  private Optional<Located> inModule(String path) {
    if (!path.startsWith(within())) {
      return Optional.empty();
    }
    String rest = path.substring(within().length());
    int src = rest.indexOf("/src/");
    if (src < 0) {
      return Optional.empty();
    }
    String module = rest.substring(0, rest.indexOf('/'));
    String inside = rest.substring(src + "/src/".length());
    if (!MODULES.contains(module) || inside.indexOf('/') < 0) {
      return Optional.empty();
    }
    return Optional.of(new Located(module, inside.substring(0, inside.indexOf('/'))));
  }

  static boolean like(String text, String... globs) {
    for (String glob : globs) {
      String regex =
          Stream.of(glob.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*"));
      if (text.matches(regex)) {
        return true;
      }
    }
    return false;
  }
}
