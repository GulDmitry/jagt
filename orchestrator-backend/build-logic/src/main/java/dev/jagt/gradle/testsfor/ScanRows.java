package dev.jagt.gradle.testsfor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.yaml.snakeyaml.Yaml;

record ScanRows(List<PathMatcher> size, List<PathMatcher> all) {

  static ScanRows of(List<String> fitnessYaml, String backend) {
    Object root = new Yaml().load(String.join("\n", fitnessYaml));
    List<PathMatcher> size = new ArrayList<>();
    List<PathMatcher> all = new ArrayList<>();
    if (root instanceof Map<?, ?> top) {
      collect(top, backend, row -> true, all);
      if (top.get("technical") instanceof Map<?, ?> technical) {
        collect(
            technical.get("maintainability"),
            backend,
            row -> "size".equals(row.get("governs")),
            size);
      }
    }
    return new ScanRows(List.copyOf(size), List.copyOf(all));
  }

  boolean governsSize(String path) {
    Path file = Path.of(path);
    return path.endsWith(".md") && size.stream().anyMatch(matcher -> matcher.matches(file));
  }

  boolean scans(String path) {
    Path file = Path.of(path);
    return all.stream().anyMatch(matcher -> matcher.matches(file));
  }

  private static void collect(
      Object node, String backend, Predicate<Map<?, ?>> wanted, List<PathMatcher> matchers) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    if ("scan".equals(map.get("engine")) || "size".equals(map.get("governs"))) {
      if (map.get("glob") instanceof String globs && wanted.test(map)) {
        for (String glob : topLevelCommas(globs)) {
          String resolved = Path.of(backend).resolve(glob.strip()).normalize().toString();
          matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + resolved));
        }
      }
      return;
    }
    map.values().forEach(child -> collect(child, backend, wanted, matchers));
  }

  private static List<String> topLevelCommas(String globs) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < globs.length(); i++) {
      char c = globs.charAt(i);
      depth += c == '{' ? 1 : c == '}' ? -1 : 0;
      if (c == ',' && depth == 0) {
        parts.add(globs.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(globs.substring(start));
    return parts;
  }
}
