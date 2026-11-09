package dev.jagt.orchestrator.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public final class AcceptanceScoreboard {

  private final List<AcceptanceScore> scores = new ArrayList<>();
  private double seconds;
  private double costUsd;

  private AcceptanceScoreboard() {}

  public static AcceptanceScoreboard empty() {
    return new AcceptanceScoreboard();
  }

  public synchronized void record(AcceptanceScore score, double took, OptionalDouble cost) {
    scores.add(score);
    seconds += took;
    costUsd += cost.orElse(0);
  }

  public synchronized String summary() {
    int rows = scores.stream().mapToInt(AcceptanceScore::rows).sum();
    int correct = scores.stream().mapToInt(AcceptanceScore::correct).sum();
    int expectedMet = scores.stream().mapToInt(AcceptanceScore::expectedMet).sum();
    int falseNotMet = scores.stream().mapToInt(AcceptanceScore::falseNotMet).sum();
    int falseMet = scores.stream().mapToInt(AcceptanceScore::falseMet).sum();
    int judgedUnprovable = scores.stream().mapToInt(AcceptanceScore::judgedUnprovable).sum();
    int expectedUnprovable = scores.stream().mapToInt(AcceptanceScore::expectedUnprovable).sum();
    long reports = scores.stream().filter(AcceptanceScore::reportMatches).count();
    long endings = scores.stream().filter(AcceptanceScore::endingMatches).count();
    return String.format(
        Locale.ROOT,
        "acceptanceEval runs=%d rows=%d correct=%d expectedMet=%d falseNotMet=%d falseMet=%d"
            + " judgedUnprovable=%d expectedUnprovable=%d reportMatches=%d endingMatches=%d"
            + " seconds=%.0f costUsd=%.2f",
        scores.size(),
        rows,
        correct,
        expectedMet,
        falseNotMet,
        falseMet,
        judgedUnprovable,
        expectedUnprovable,
        reports,
        endings,
        seconds,
        costUsd);
  }
}
