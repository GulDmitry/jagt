package dev.jagt.orchestrator.task;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * When jagt stops a task as a loop of its own making: {@code stalledRounds} rounds running that brought no new note
 * and left the same job failing, a ship asked past {@code ships} within {@code window} that answered a round
 * bringing no new note, or a round of one step past {@code rounds} sent running.
 */
public record LoopBreaker(int stalledRounds, int ships, int rounds, Duration window) {

    /** The breaker as configured at the moment it is asked. */
    @FunctionalInterface
    public interface Current {
        LoopBreaker current();
    }

    private static final Duration ROUND_WINDOW = Duration.ofHours(24);

    /** A round of {@code step} sent {@code rounds} times running within 24h since the lift is the last. */
    public Optional<TaskStop> beforeRound(String step, Turn turn, RoundTrail trail, long now) {
        long from = Math.max(trail.since(), now - ROUND_WINDOW.toMillis());
        long running = turn.step().equals(step) ? turn.run().stream().filter(at -> at > from).count() : 0;
        return running < rounds ? Optional.empty()
                : Optional.of(new TaskStop(TaskStop.Trigger.ROUNDS, now, step + " sent " + running
                        + " rounds running in " + ROUND_WINDOW.toHours() + "h; the next one was not"));
    }

    public Optional<TaskStop> afterRound(RoundTrail trail, List<StatusChange> history, long now) {
        int stalled = stalled(trail.rounds());
        return stalled < stalledRounds ? Optional.empty()
                : Optional.of(new TaskStop(TaskStop.Trigger.STALLED, now,
                        stalled + " rounds running brought no new note; " + evidence(trail, history, now)));
    }

    public Optional<TaskStop> beforeShip(RoundTrail trail, List<StatusChange> history, long now) {
        return shipped(trail, history, now) < ships ? Optional.empty()
                : Optional.of(new TaskStop(TaskStop.Trigger.SHIPS, now,
                        "a ship asked past " + ships + " without a new note in " + window.toHours() + "h; " + evidence(trail, history, now)));
    }

    private static int stalled(List<RoundTrail.Round> rounds) {
        int stalled = 0;
        for (int i = rounds.size() - 1; i > 0; i--) {
            RoundTrail.Round round = rounds.get(i);
            RoundTrail.Round before = rounds.get(i - 1);
            if (!round.repeats(before.notes()) || !round.failing().equals(before.failing())) {
                break;
            }
            stalled++;
        }
        return stalled;
    }

    private long shipped(RoundTrail trail, List<StatusChange> history, long now) {
        long from = Math.max(trail.since(), now - window.toMillis());
        return history.stream().filter(step -> step.status() == TaskStatus.SHIPPING && step.at() > from)
                .filter(step -> !broughtNewNote(trail.rounds(), step.at())).count();
    }

    private static boolean broughtNewNote(List<RoundTrail.Round> rounds, long shipAt) {
        int answered = -1;
        for (int i = 0; i < rounds.size() && rounds.get(i).at() <= shipAt; i++) {
            answered = i;
        }
        return answered > 0 && !rounds.get(answered).repeats(rounds.get(answered - 1).notes());
    }

    private String evidence(RoundTrail trail, List<StatusChange> history, long now) {
        return "threads per round: " + trail.rounds().stream().map(round -> String.valueOf(round.threads()))
                .collect(Collectors.joining(", "))
                + "; ships without a new note in " + window.toHours() + "h: " + shipped(trail, history, now);
    }
}
