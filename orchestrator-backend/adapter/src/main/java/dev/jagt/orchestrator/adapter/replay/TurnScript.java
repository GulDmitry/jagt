package dev.jagt.orchestrator.adapter.replay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** One task's recorded turns in the order they opened, each with the moves that answered it. */
public final class TurnScript {

    static final String LAUNCH = "launch";

    private record Group(String step, List<RecordingLine.Move> moves) {
    }

    /** {@code divergence} blank where the moves answer the turn. */
    public record Reply(List<RecordingLine.Move> moves, String divergence) {

        public boolean diverged() {
            return !divergence.isBlank();
        }
    }

    private final Deque<Group> groups;

    private TurnScript(Deque<Group> groups) {
        this.groups = groups;
    }

    /** Moves before the task's first opened turn answer its launch. */
    public static TurnScript of(String task, List<RecordingLine> recording) {
        Deque<Group> groups = new ArrayDeque<>();
        for (RecordingLine line : recording) {
            if (line instanceof RecordingLine.Opened opened && opened.task().equals(task)) {
                groups.add(new Group(opened.step(), new ArrayList<>()));
            } else if (line instanceof RecordingLine.Move move && move.task().equals(task)) {
                if (groups.isEmpty()) {
                    groups.add(new Group(LAUNCH, new ArrayList<>()));
                }
                groups.getLast().moves().add(move);
            }
        }
        return new TurnScript(groups);
    }

    public List<RecordingLine.Move> launched() {
        return !groups.isEmpty() && groups.getFirst().step().equals(LAUNCH) ? groups.removeFirst().moves() : List.of();
    }

    public Reply opened(String step) {
        Group next = groups.peekFirst();
        if (next == null) {
            return new Reply(List.of(), "turn opened at " + step + " with no recorded move");
        }
        if (!next.step().equals(step)) {
            return new Reply(List.of(), "recorded turn at " + next.step() + " never opened, the turn opened at " + step);
        }
        return new Reply(groups.removeFirst().moves(), "");
    }

    public boolean drained() {
        return groups.isEmpty();
    }
}
