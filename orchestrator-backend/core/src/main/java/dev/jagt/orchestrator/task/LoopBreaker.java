package dev.jagt.orchestrator.task;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * When jagt stops a task as a loop of its own making: {@code stalledRounds} rounds running that resolved no thread
 * and left the same job failing, or a ship asked past {@code ships} within {@code window} that answered a round
 * bringing the open threads to no new low.
 */
public record LoopBreaker(int stalledRounds, int ships, Duration window) {

    public Optional<LoopWatch.Stop> afterRound(LoopWatch watch, List<StatusChange> history, long now) {
        int stalled = stalled(watch.rounds());
        return stalled < stalledRounds ? Optional.empty()
                : Optional.of(new LoopWatch.Stop(LoopWatch.Trigger.STALLED, now,
                        stalled + " rounds running resolved no thread; " + trail(watch, history, now)));
    }

    public Optional<LoopWatch.Stop> beforeShip(LoopWatch watch, List<StatusChange> history, long now) {
        return shipped(watch, history, now) < ships ? Optional.empty()
                : Optional.of(new LoopWatch.Stop(LoopWatch.Trigger.SHIPS, now,
                        "a ship asked past " + ships + " without a new low in " + window.toHours() + "h; " + trail(watch, history, now)));
    }

    private static int stalled(List<LoopWatch.Round> rounds) {
        int stalled = 0;
        for (int i = rounds.size() - 1; i > 0; i--) {
            LoopWatch.Round round = rounds.get(i);
            LoopWatch.Round before = rounds.get(i - 1);
            if (round.threads() < before.threads() || !round.failing().equals(before.failing())) {
                break;
            }
            stalled++;
        }
        return stalled;
    }

    private long shipped(LoopWatch watch, List<StatusChange> history, long now) {
        long from = Math.max(watch.since(), now - window.toMillis());
        return history.stream().filter(step -> step.status() == TaskStatus.SHIPPING && step.at() > from)
                .filter(step -> !reachedNewLow(watch.rounds(), step.at())).count();
    }

    private static boolean reachedNewLow(List<LoopWatch.Round> rounds, long shipAt) {
        int answered = -1;
        for (int i = 0; i < rounds.size() && rounds.get(i).at() <= shipAt; i++) {
            answered = i;
        }
        return answered > 0 && rounds.subList(0, answered).stream().mapToInt(LoopWatch.Round::threads).min()
                .getAsInt() > rounds.get(answered).threads();
    }

    private String trail(LoopWatch watch, List<StatusChange> history, long now) {
        return "threads per round: " + watch.rounds().stream().map(round -> String.valueOf(round.threads()))
                .collect(Collectors.joining(", "))
                + "; ships without a new low in " + window.toHours() + "h: " + shipped(watch, history, now);
    }
}
