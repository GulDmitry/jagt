package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/** The rounds relayed into a task since {@code since}, the last lift. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoundTrail(List<Round> rounds, long since) {

    public static final int MAX_ROUNDS = 20;

    /** {@code failing} is the failing job's name, blank while the checks are not red. */
    public record Round(long at, int threads, String failing, String digest) {
    }

    public RoundTrail {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    public static RoundTrail none() {
        return new RoundTrail(List.of(), 0);
    }

    public static RoundTrail liftedAt(long now) {
        return new RoundTrail(List.of(), now);
    }

    public RoundTrail withRound(Round round) {
        List<Round> grown = new ArrayList<>(rounds);
        grown.add(round);
        return new RoundTrail(grown.subList(Math.max(0, grown.size() - MAX_ROUNDS), grown.size()), since);
    }

    public boolean relayed(String digest) {
        return !rounds.isEmpty() && rounds.getLast().digest().equals(digest);
    }
}
