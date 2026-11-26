package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AdapterPorts {

  private static final Pattern HEADER =
      Pattern.compile(
          "(?:public\\s+|final\\s+|abstract\\s+)*(class|interface)\\s+(\\w+)(?:<[^>{]*>)?(?:\\s+e"
              + "xtends\\s+(\\w+))?"
              + "(?:\\s+implements\\s+([^{]+))?\\s*\\{");

  private final Map<String, String> sources = new HashMap<>();
  private final Map<String, String> supers = new HashMap<>();
  private final Map<String, Set<String>> direct = new HashMap<>();
  private final Set<String> concrete = new HashSet<>();

  AdapterPorts() {
    SourceRoots.javaFilesUnder(Path.of("adapter/src/main/java")).forEach(this::register);
  }

  Map<String, List<String>> adaptersByPort() {
    Set<String> ports = new HashSet<>();
    SourceRoots.javaFilesUnder(Path.of("core/src/main/java/dev/jagt/orchestrator/port"))
        .forEach(file -> ports.add(file.getFileName().toString().replace(".java", "")));
    Map<String, List<String>> adapters = new HashMap<>();
    concrete.forEach(name ->
        portsOf(name).stream()
            .filter(ports::contains)
            .forEach(port -> adapters.computeIfAbsent(port, key -> new ArrayList<>()).add(name)));
    return adapters;
  }

  String sourceOf(String adapter) {
    return sources.getOrDefault(adapter, "");
  }

  boolean wiredAsFallback(String port, String adapter) {
    Pattern fallback =
        Pattern.compile(
            "@ConditionalOnMissingBean\\(" + port + "\\.class\\)\\s+" + adapter + "\\s+\\w+\\(\\)");
    return sources.values().stream().anyMatch(source -> fallback.matcher(source).find());
  }

  private void register(Path file) {
    String source = SourceRoots.read(file);
    Matcher header = HEADER.matcher(source.replaceAll("(?s)/\\*.*?\\*/", ""));
    if (header.find() && header.group(1).equals("class")) {
      String name = header.group(2);
      sources.put(name, source);
      supers.put(name, header.group(3));
      direct.put(name, interfaces(header.group(4)));
      if (!header.group(0).contains("abstract")) {
        concrete.add(name);
      }
    }
  }

  private Set<String> portsOf(String name) {
    Set<String> found = new TreeSet<>(direct.getOrDefault(name, Set.of()));
    String parent = supers.get(name);
    if (parent != null) {
      found.addAll(portsOf(parent));
    }
    return found;
  }

  private static Set<String> interfaces(String implemented) {
    Set<String> names = new HashSet<>();
    if (implemented != null) {
      for (String each : implemented.replaceAll("<[^>]*>", "").split(",", 0)) {
        names.add(each.strip().replaceAll(".*\\.", ""));
      }
    }
    return names;
  }
}
