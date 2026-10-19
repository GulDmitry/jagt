package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyStartsTest {

    private static final Clock MONDAY = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock TUESDAY = Clock.fixed(Instant.parse("2026-10-20T00:30:00Z"), ZoneOffset.UTC);

    @Test
    void stopsTheDayAtTheCeiling(@TempDir Path root) {
        DailyStarts starts = new DailyStarts(pathsIn(root), MONDAY);

        starts.take(2);
        starts.take(2);

        assertThat(starts.standing()).contains(new ActStop(ActStop.Cause.DAILY_CEILING, 2));
        assertThat(root.resolve("starts-stopped")).exists();
    }

    @Test
    void stopsNothingBelowTheCeiling(@TempDir Path root) {
        DailyStarts starts = new DailyStarts(pathsIn(root), MONDAY);

        starts.take(2);

        assertThat(starts.standing()).isEmpty();
    }

    @Test
    void liftsItselfOnTheNextLocalDay(@TempDir Path root) {
        OrchestratorPaths paths = pathsIn(root);
        new DailyStarts(paths, MONDAY).take(1);

        DailyStarts tuesday = new DailyStarts(paths, TUESDAY);

        assertThat(tuesday.standing()).isEmpty();
        assertThat(root.resolve("starts-stopped")).doesNotExist();
    }

    @Test
    void countsFromZeroOnANewDay(@TempDir Path root) {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(MONDAY.instant(), MONDAY.instant(), TUESDAY.instant());
        DailyStarts starts = new DailyStarts(pathsIn(root), clock);

        starts.take(2);
        starts.take(2);

        assertThat(starts.standing()).isEmpty();
    }

    @Test
    void countsFromZeroAgainOnceLifted(@TempDir Path root) {
        DailyStarts starts = new DailyStarts(pathsIn(root), MONDAY);
        starts.take(2);
        starts.take(2);

        boolean wasStopped = starts.lift();
        starts.take(2);

        assertThat(wasStopped).isTrue();
        assertThat(starts.standing()).isEmpty();
    }

    private static OrchestratorPaths pathsIn(Path root) {
        return new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
    }
}
