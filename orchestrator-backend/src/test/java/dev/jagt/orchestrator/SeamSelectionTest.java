package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SeamSelectionTest {

  private static final Pattern HEADER =
      Pattern.compile(
          "(?:public\\s+|final\\s+|abstract\\s+)*(class|interface)\\s+(\\w+)(?:<[^>{]*>)?(?:\\s+extends\\s+(\\w+))?"
              + "(?:\\s+implements\\s+([^{]+))?\\s*\\{");
  private static final Pattern SELECTED =
      Pattern.compile("@ConditionalOnExpression\\(|@ConditionalOnProperty\\([^)]*orchestrator");
  private static final Set<String> RUN_TOGETHER = Set.of("StartupCheck", "Notifier");

  @Test
  void selectsEveryAdapterOfAPortWithSeveralAdaptersByAnOrchestratorKey() {
    Set<String> ports = portNames();
    Map<String, List<String>> adapters = new HashMap<>();
    Map<String, String> sources = new HashMap<>();
    Map<String, String> supers = new HashMap<>();
    Map<String, Set<String>> direct = new HashMap<>();
    Set<String> concrete = new HashSet<>();
    sourcesUnder(Path.of("adapter/src/main/java"))
        .forEach(
            file -> {
              String source = read(file);
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
            });
    concrete.forEach(
        name ->
            portsOf(name, supers, direct).stream()
                .filter(ports::contains)
                .forEach(
                    port -> adapters.computeIfAbsent(port, key -> new ArrayList<>()).add(name)));

    List<String> unselected =
        adapters.entrySet().stream()
            .filter(port -> port.getValue().size() > 1 && !RUN_TOGETHER.contains(port.getKey()))
            .flatMap(port -> port.getValue().stream().map(name -> port.getKey() + " <- " + name))
            .filter(
                line ->
                    !SELECTED.matcher(sources.get(line.substring(line.indexOf("<- ") + 3))).find())
            .sorted()
            .toList();

    assertThat(unselected).isEmpty();
  }

  private static Set<String> portsOf(
      String name, Map<String, String> supers, Map<String, Set<String>> direct) {
    Set<String> found = new TreeSet<>(direct.getOrDefault(name, Set.of()));
    String parent = supers.get(name);
    if (parent != null) {
      found.addAll(portsOf(parent, supers, direct));
    }
    return found;
  }

  private static Set<String> interfaces(String implemented) {
    Set<String> names = new HashSet<>();
    if (implemented != null) {
      for (String each : implemented.replaceAll("<[^>]*>", "").split(",")) {
        names.add(each.strip().replaceAll(".*\\.", ""));
      }
    }
    return names;
  }

  private static Set<String> portNames() {
    Set<String> names = new HashSet<>();
    sourcesUnder(Path.of("core/src/main/java/dev/jagt/orchestrator/port"))
        .forEach(file -> names.add(file.getFileName().toString().replace(".java", "")));
    return names;
  }

  private static List<Path> sourcesUnder(Path root) {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
