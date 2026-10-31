package dev.jagt.orchestrator.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

public final class QaScoreboard {

    private final List<QaScore> scores = new ArrayList<>();
    private double seconds;
    private double costUsd;

    private QaScoreboard() {
    }

    public static QaScoreboard empty() {
        return new QaScoreboard();
    }

    public synchronized void record(QaScore score, double took, OptionalDouble cost) {
        scores.add(score);
        seconds += took;
        costUsd += cost.orElse(0);
    }

    public synchronized String summary() {
        int rows = scores.stream().mapToInt(QaScore::rows).sum();
        int correct = scores.stream().mapToInt(QaScore::correct).sum();
        int expectedMet = scores.stream().mapToInt(QaScore::expectedMet).sum();
        int falseNotMet = scores.stream().mapToInt(QaScore::falseNotMet).sum();
        int falseMet = scores.stream().mapToInt(QaScore::falseMet).sum();
        int judgedUnprovable = scores.stream().mapToInt(QaScore::judgedUnprovable).sum();
        int expectedUnprovable = scores.stream().mapToInt(QaScore::expectedUnprovable).sum();
        long reports = scores.stream().filter(QaScore::reportMatches).count();
        long endings = scores.stream().filter(QaScore::endingMatches).count();
        return String.format(Locale.ROOT, "qaEval runs=%d rows=%d correct=%d expectedMet=%d falseNotMet=%d falseMet=%d"
                        + " judgedUnprovable=%d expectedUnprovable=%d reportMatches=%d endingMatches=%d"
                        + " seconds=%.0f costUsd=%.2f", scores.size(), rows, correct, expectedMet, falseNotMet,
                falseMet, judgedUnprovable, expectedUnprovable, reports, endings, seconds, costUsd);
    }
}
