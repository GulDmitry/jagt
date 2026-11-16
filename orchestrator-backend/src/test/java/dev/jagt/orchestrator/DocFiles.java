package dev.jagt.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DocFiles {

  private DocFiles() {}

  static List<Path> markdownIn(Path directory) {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.toString().endsWith(".md")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Map<Path, String> contentsIn(Path directory, String suffix) {
    return filesEnding(directory, suffix).stream()
        .collect(Collectors.toMap(path -> path, DocFiles::read));
  }

  private static List<Path> filesEnding(Path directory, String suffix) {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(path -> path.toString().endsWith(suffix)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Path> specs() {
    try (Stream<Path> capabilities = Files.list(Path.of("../openspec/specs"))) {
      return capabilities
          .map(capability -> capability.resolve("spec.md"))
          .filter(Files::isRegularFile)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Path> documents() {
    return Stream.concat(
            Stream.of("..", "../docs", "../docs/features", "../docs/rules", "../.claude/rules")
                .flatMap(directory -> markdownIn(Path.of(directory)).stream()),
            specs().stream())
        .toList();
  }

  static List<String> headingSlugs(Path document) {
    if (!Files.isRegularFile(document)) {
      return List.of();
    }
    return withoutCodeBlocks(document)
        .lines()
        .filter(line -> line.matches("#{1,6} .*"))
        .map(line ->
            line.replaceFirst("#+ ", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} _-]", "")
                .replace(' ', '-'))
        .toList();
  }

  static Map<String, String> requirementTexts(Path spec) {
    return Stream.of(withoutCodeBlocks(spec).split("(?m)^### Requirement:"))
        .skip(1)
        .collect(
            Collectors.toMap(
                block -> block.lines().findFirst().orElse("").trim(),
                block ->
                    block
                        .lines()
                        .skip(1)
                        .takeWhile(line -> !line.matches("#{1,6}\\s.*"))
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .collect(Collectors.joining("\n")),
                (first, second) -> first,
                LinkedHashMap::new));
  }

  static String purpose(Path spec) {
    Matcher found =
        Pattern.compile("(?ms)^##[ \\t]+Purpose[ \\t]*$(.*?)(?=^#{1,2}[ \\t])").matcher(read(spec));
    return found.find() ? found.group(1).trim() : "";
  }

  static List<String> namesIn(Path directory) {
    return markdownIn(directory).stream().map(path -> path.getFileName().toString()).toList();
  }

  static String withoutCodeBlocks(Path document) {
    return read(document).replaceAll("(?s)```.*?```", "");
  }

  static List<String> specDirectories() {
    try (Stream<Path> specs = Files.list(Path.of("../openspec/specs"))) {
      return specs
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static boolean describesInOrder(String dist, String key) {
    int from = 0;
    for (String segment : key.replaceFirst("^orchestrator\\.", "").split("\\.", 0)) {
      Matcher found =
          Pattern.compile("(?m)^\\s*#?\\s*" + Pattern.quote(segment) + ":").matcher(dist);
      if (!found.find(from)) {
        return false;
      }
      from = found.end();
    }
    return true;
  }
}
