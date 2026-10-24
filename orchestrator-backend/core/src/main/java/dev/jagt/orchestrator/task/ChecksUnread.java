package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** A read that could not list a task's checks: when, and the read's own words for why. */
public record ChecksUnread(long at, String cause) {

    /** A second read can still be a blip spanning one poll; a third running spans two, as rounds running do. */
    public static final int CEILING = 3;

    public ChecksUnread {
        cause = cause == null ? "" : cause.strip();
    }

    /** The stop once {@link #CEILING} reads running since the last lift could not list the checks. */
    public static Optional<TaskStop> stop(List<ChecksUnread> running, RoundTrail trail, long now) {
        List<ChecksUnread> sinceLift = running.stream().filter(read -> read.at() > trail.since()).toList();
        if (sinceLift.size() < CEILING) {
            return Optional.empty();
        }
        String causes = sinceLift.stream().map(read -> read.cause().isEmpty() ? "the read gave no cause" : read.cause())
                .distinct().collect(Collectors.joining("; "));
        return Optional.of(new TaskStop(TaskStop.Trigger.CHECKS, now,
                sinceLift.size() + " reads running could not list the checks: " + causes));
    }
}
