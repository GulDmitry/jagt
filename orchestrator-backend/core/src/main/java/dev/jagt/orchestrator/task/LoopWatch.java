package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The rounds relayed into a task since {@code since}, and the stop they tripped. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoopWatch(List<Round> rounds, long since, @Nullable Stop stop) {

    public static final int MAX_ROUNDS = 20;

    /** {@code failing} is the failing job's name, blank while the checks are not red. */
    public record Round(long at, int threads, String failing, String digest) {
    }

    /** {@code next} is what a human reads after the evidence. */
    public enum Trigger {
        STALLED(Trigger.DEFECT), SHIPS(Trigger.DEFECT), MISS("Add a case if one should send it"),
        ROUNDS("Read what the session made of the last one");

        private static final String DEFECT = "Likely a jagt defect: inspect the sweeps in the log";

        private final String next;

        Trigger(String next) {
            this.next = next;
        }
    }

    public record Stop(Trigger trigger, long at, String evidence) {

        public String reading() {
            return evidence + ". " + trigger.next + ", then lift or close";
        }
    }

    public LoopWatch {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }

    public static LoopWatch none() {
        return new LoopWatch(List.of(), 0, null);
    }

    public static LoopWatch liftedAt(long now) {
        return new LoopWatch(List.of(), now, null);
    }

    public LoopWatch withRound(Round round) {
        List<Round> grown = new ArrayList<>(rounds);
        grown.add(round);
        return new LoopWatch(grown.subList(Math.max(0, grown.size() - MAX_ROUNDS), grown.size()), since, stop);
    }

    public LoopWatch stopped(Stop stop) {
        return new LoopWatch(rounds, since, stop);
    }

    public boolean relayed(String digest) {
        return !rounds.isEmpty() && rounds.getLast().digest().equals(digest);
    }
}
