package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.job.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Keeps the Master session running while a human wants it. One job rather than a start and a watchdog: starting
 * it and finding it gone are the same act, and its own log is what says whether it is there.
 */
@Service
@RequiredArgsConstructor
public class MasterSessionJob implements Job {

    private final MasterSession master;

    @Override
    public String id() {
        return "master";
    }

    @Override
    public String describe() {
        return "keep the unattended reviewer's session running while `master.mode` wants one";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(30);
    }

    @Override
    public void run() {
        master.startIfWanted();
    }
}
