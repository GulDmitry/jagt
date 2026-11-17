package dev.jagt.mutationscope;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record ChangedLines(Map<String, Set<Integer>> byFile) {

  static ChangedLines read(Path file) {
    try {
      Map<String, Set<Integer>> byFile = new HashMap<>();
      for (String row : Files.readAllLines(file)) {
        int colon = row.lastIndexOf(':');
        byFile.put(
            row.substring(0, colon),
            Arrays.stream(row.substring(colon + 1).split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet()));
      }
      return new ChangedLines(byFile);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  boolean contains(String sourcePath, int line) {
    return byFile.getOrDefault(sourcePath, Set.of()).contains(line);
  }
}
