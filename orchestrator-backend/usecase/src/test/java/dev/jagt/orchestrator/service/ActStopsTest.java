package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ActStop.Cause;
import dev.jagt.orchestrator.service.master.MasterBrake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ActStopsTest {

    @Test
    void namesThreeBadEndingsWhereActStoppedFileStands(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");

        assertThat(stopsIn(root).standing()).contains(new ActStop(Cause.STRIKES));
    }

    @Test
    void liftsNothingWhereNoStopStands(@TempDir Path root) {
        assertThat(stopsIn(root).lift()).isFalse();
    }

    @Test
    void liftsTheStop(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("act-stopped"), "ABC-1\n");
        ActStops stops = stopsIn(root);

        boolean lifted = stops.lift();

        assertThat(lifted).isTrue();
        assertThat(stops.standing()).isEmpty();
    }

    private static ActStops stopsIn(Path root) {
        OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()));
        return new ActStops(new MasterBrake(mock(StateService.class), paths));
    }
}
