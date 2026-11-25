package dev.jagt.orchestrator.eval;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record CodeReviewCase(
    String name, String description, String diff, String botNote, Expected expected) {

  public record Expected(boolean clean, List<Finding> findings, Note note) {}

  /** The size of a posted note; zero words means the case does not hold it. */
  public record Note(int maxLines, int maxWords) {

    public static final Note UNHELD = new Note(0, 0);
  }

  public record Finding(String file, Pattern pattern, Set<String> severities) {}
}
