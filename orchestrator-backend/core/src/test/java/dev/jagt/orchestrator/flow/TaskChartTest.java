package dev.jagt.orchestrator.flow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class TaskChartTest {

    @Test
    void startsAtNewOnAChartTheEngineValidatedAsReachableAndLeavable() {
        assertThat(TaskChart.chart().initial().toPlain()).containsExactly(
                entry("lifecycle", "NEW"));
    }

    @Test
    void rendersTheDiagramTheDocsPageShows() throws IOException {
        String page = Files.readString(Path.of("../../docs/task-chart.md"));

        assertThat(page).isEqualTo("# The task lifecycle chart\n\n```mermaid\n" + TaskChart.chart().render() + "```\n");
    }
}
