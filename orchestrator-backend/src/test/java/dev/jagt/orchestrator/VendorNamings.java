package dev.jagt.orchestrator;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class VendorNamings {

  private static final Pattern VENDOR =
      Pattern.compile(
          "(?i)\\b(claude|anthropic|codex|openai|gemini|tmux|kitty|"
              + "iterm2?|wezterm|ghostty|warp|intellij|osascript|gitlab|github|glab|gh|bitbucket|gi"
              + "tea|jira|gradle|gradlew|"
              + "maven|mvn|npm|fable|opus|sonnet|haiku)\\b");

  private VendorNamings() {}

  record Naming(Path file, int line, String word) {

    String key() {
      return file + " " + word;
    }

    String where() {
      return file + ":" + line + " " + word;
    }
  }

  static Stream<Naming> outsideTheAdapters() {
    return SourceRoots.mainJavaFiles()
        .filter(file -> !file.startsWith("adapter"))
        .flatMap(file -> {
          List<String> lines = SourceRoots.read(file).lines().toList();
          return IntStream.range(0, lines.size())
              .boxed()
              .flatMap(i -> words(lines.get(i)).map(word -> new Naming(file, i + 1, word)));
        });
  }

  private static Stream<String> words(String line) {
    if (line.strip().startsWith("*") || line.strip().startsWith("/*")) {
      return Stream.empty();
    }
    return VENDOR
        .matcher(line.replaceAll("//.*$", ""))
        .results()
        .map(match -> match.group().toLowerCase(Locale.ROOT));
  }
}
