package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;

/** Counts the day's unattended starts in memory and stops them for the rest of the local day at the ceiling. */
@Service
@Slf4j
public class DailyStarts {

    private final OrchestratorPaths paths;
    private final Clock clock;
    private LocalDate day;
    private int taken;

    @Autowired
    public DailyStarts(OrchestratorPaths paths) {
        this(paths, Clock.systemDefaultZone());
    }

    DailyStarts(OrchestratorPaths paths, Clock clock) {
        this.paths = paths;
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    public synchronized void take(int ceiling) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            taken = 0;
        }
        taken++;
        if (taken >= ceiling && ceiling().isEmpty()) {
            stop(today, ceiling);
        }
    }

    /** The ceiling of the stop standing today. A stop of an earlier day is removed here, so it lifts by itself. */
    public synchronized OptionalInt ceiling() {
        try {
            if (!Files.exists(paths.startsStopped())) {
                return OptionalInt.empty();
            }
            String[] written = Files.readString(paths.startsStopped()).strip().split(" ");
            if (LocalDate.parse(written[0]).isBefore(LocalDate.now(clock))) {
                Files.deleteIfExists(paths.startsStopped());
                return OptionalInt.empty();
            }
            return OptionalInt.of(Integer.parseInt(written[1]));
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("daily stop unreadable").addKeyValue("file", paths.startsStopped())
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "unattended starts stay stopped").log();
            return OptionalInt.of(0);
        }
    }

    /** True where it was stopped. The count starts again from zero. */
    public synchronized boolean lift() {
        taken = 0;
        try {
            return Files.deleteIfExists(paths.startsStopped());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove " + paths.startsStopped(), e);
        }
    }

    private void stop(LocalDate today, int ceiling) {
        try {
            Files.writeString(paths.startsStopped(), today + " " + ceiling + "\n");
            log.atWarn().setMessage("daily starts stopped").addKeyValue("ceiling", ceiling)
                    .addKeyValue("cause", ceiling + " unattended starts today")
                    .addKeyValue("effect", "unattended starts wait until tomorrow or `act`").log();
        } catch (IOException e) {
            log.atError().setMessage("daily starts not stopped").addKeyValue("file", paths.startsStopped())
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "unattended starts go on").log();
        }
    }
}
