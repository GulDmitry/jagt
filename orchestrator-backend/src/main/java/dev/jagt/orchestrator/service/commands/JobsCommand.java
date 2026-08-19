package dev.jagt.orchestrator.service.commands;

import dev.jagt.orchestrator.job.Jobs;
import dev.jagt.orchestrator.service.DurationFormat;
import dev.jagt.orchestrator.service.GlobalCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobsCommand implements GlobalCommand {

    private final Jobs jobs;

    @Override
    public String id() {
        return "jobs";
    }

    @Override
    public String hint() {
        return "what runs unattended, how often, and when each one runs next";
    }

    @Override
    public boolean report() {
        return true;
    }

    @Override
    public String run(String tail) {
        long now = System.currentTimeMillis();
        return render(jobs.statuses(now), now);
    }

    static String render(List<Jobs.Status> statuses, long now) {
        if (statuses.isEmpty()) {
            return "no jobs registered — nothing runs unattended.";
        }
        List<String> lines = new ArrayList<>();
        lines.add("jobs (nobody watches these):");
        for (Jobs.Status job : statuses) {
            lines.add("  %-13s %-12s %-14s %-14s %s".formatted(job.id(), cadence(job), last(job, now),
                    next(job, now), job.describe()));
            if (job.lastError() != null) {
                lines.add(" ".repeat(16) + "last run failed: " + job.lastError());
            }
        }
        return String.join("\n", lines);
    }

    private static String cadence(Jobs.Status job) {
        return job.every() == null ? "at startup" : "every " + DurationFormat.countdown(job.every().toMillis());
    }

    private static String last(Jobs.Status job, long now) {
        if (job.running()) {
            return "running now";
        }
        return job.lastStartedAt() == null ? "never run" : "ran " + DurationFormat.compact(now - job.lastStartedAt())
                + " ago";
    }

    private static String next(Jobs.Status job, long now) {
        return job.nextRunAt() == null ? "-" : "next in " + DurationFormat.countdown(job.nextRunAt() - now);
    }
}
