package dev.jagt.gradle.testsfor;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

record SizeRows(List<PathMatcher> matchers) {

  static SizeRows of(List<String> fitnessYaml, String backend) {
    List<PathMatcher> matchers = new ArrayList<>();
    Object root = new Yaml().load(String.join("\n", fitnessYaml));
    if (root instanceof Map<?, ?> top && top.get("technical") instanceof Map<?, ?> technical) {
      collect(technical.get("maintainability"), backend, matchers);
    }
    return new SizeRows(List.copyOf(matchers));
  }

  boolean governs(String path) {
    Path file = Path.of(path);
    return path.endsWith(".md") && matchers.stream().anyMatch(matcher -> matcher.matches(file));
  }

  private static void collect(Object node, String backend, List<PathMatcher> matchers) {
    if (!(node instanceof Map<?, ?> map)) {
      return;
    }
    if ("size".equals(map.get("governs")) && map.get("glob") instanceof String globs) {
      for (String glob : globs.split(",")) {
        String resolved = Path.of(backend).resolve(glob.strip()).normalize().toString();
        matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + resolved));
      }
      return;
    }
    map.values().forEach(child -> collect(child, backend, matchers));
  }
}
