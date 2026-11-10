package dev.jagt.orchestrator.stand;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum StandMode {
  JUDGE("approve-plan,ship,deploy", true),
  ACT("approve-plan", false);

  private final String humanVerbs;
  private final boolean humanEndsTheRun;

  StandMode(String humanVerbs, boolean humanEndsTheRun) {
    this.humanVerbs = humanVerbs;
    this.humanEndsTheRun = humanEndsTheRun;
  }

  public boolean humanEndsTheRun() {
    return humanEndsTheRun;
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean pressedByTheHuman(String verb) {
    return List.of(humanVerbs.split(",")).contains(verb);
  }

  public static List<StandMode> selected(String ids) {
    if (ids.isBlank()) {
      return List.of(values());
    }
    return Arrays.stream(ids.split(","))
        .map(String::strip)
        .map(id -> valueOf(id.toUpperCase(Locale.ROOT)))
        .toList();
  }
}
