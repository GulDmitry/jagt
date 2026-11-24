package dev.jagt.orchestrator.eval;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public record CodeReviewCase(
    String name, String description, String diff, String botNote, Expected expected) {

  public record Expected(boolean clean, List<Finding> findings) {}

  public record Finding(String file, Pattern pattern, Set<String> severities) {}
}
