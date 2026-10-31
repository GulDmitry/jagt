package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads a state file written when the Master's left step, unanswered question, deploy press and seal were marks on the turn, once. */
final class LegacyMaster {

    private static final String LEFT = "left";
    private static final String UNANSWERED = "unanswered";
    private static final String PRESSED_DEPLOY = "press:deploy";
    private static final String SEAL = "master-review:seal";
    private static final List<String> MARKS = List.of(LEFT, UNANSWERED, PRESSED_DEPLOY, SEAL);

    private LegacyMaster() {
    }

    static @Nullable Master of(@Nullable Master stored, @Nullable Turn turn) {
        if (stored != null || turn == null) {
            return stored;
        }
        Master read = new Master(mark(turn, LEFT), mark(turn, UNANSWERED), mark(turn, PRESSED_DEPLOY),
                mark(turn, SEAL), 0);
        return read.isEmpty() ? null : read;
    }

    static @Nullable Turn withoutMarks(@Nullable Turn turn) {
        if (turn == null || MARKS.stream().noneMatch(turn.marks()::containsKey)) {
            return turn;
        }
        Map<String, String> kept = new HashMap<>(turn.marks());
        kept.keySet().removeAll(MARKS);
        return new Turn(turn.step(), turn.id(), turn.digest(), turn.since(), kept, turn.run());
    }

    private static String mark(Turn turn, String name) {
        String value = turn.mark(name);
        return value == null ? "" : value;
    }
}
