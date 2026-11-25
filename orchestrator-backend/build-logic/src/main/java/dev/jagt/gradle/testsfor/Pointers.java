package dev.jagt.gradle.testsfor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

record Pointers(Path directory) {

  private static final Pattern GLOB = Pattern.compile("^\\s+- \"(.+)\"$");
  private static final Pattern TARGET = Pattern.compile("\\]\\(\\.\\./\\.\\./([^)#]+)\\)");

  List<String> readsFor(List<String> paths) {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    TreeSet<String> reads = new TreeSet<>();
    try (Stream<Path> files = Files.list(directory)) {
      files
          .filter(file -> file.getFileName().toString().endsWith(".md"))
          .forEach(pointer -> addTargets(pointer, paths, reads));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return List.copyOf(reads);
  }

  private static void addTargets(Path pointer, List<String> paths, TreeSet<String> reads) {
    String text = read(pointer);
    boolean governs =
        text.lines()
            .map(GLOB::matcher)
            .filter(Matcher::matches)
            .map(found -> FileSystems.getDefault().getPathMatcher("glob:" + found.group(1)))
            .anyMatch(glob -> paths.stream().anyMatch(path -> matches(glob, path)));
    if (governs) {
      TARGET.matcher(text).results().forEach(link -> reads.add(link.group(1)));
    }
  }

  private static boolean matches(PathMatcher glob, String path) {
    return glob.matches(Path.of(path)) || glob.matches(Path.of("/" + path));
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
