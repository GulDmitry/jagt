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
                entry("lifecycle", "NEW"), entry("stop", "NONE"), entry("plan", "OPEN"), entry("report", "PLAIN"),
                entry("turn", "CLOSED"), entry("deploy", "NONE"), entry("session", "NONE"));
    }

    @Test
    void rendersTheDiagramTheDocsPageShows() throws IOException {
        String[] regions = TaskChart.chart().render().split("    --\n");

        assertThat(Files.readString(Path.of("../../docs/task-chart.md")))
                .isEqualTo("# The task lifecycle chart\n\n```mermaid\n" + regions[0] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-stop-chart.md")))
                .isEqualTo("# The task stop chart\n\n```mermaid\nstateDiagram-v2\n" + regions[1] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-plan-chart.md")))
                .isEqualTo("# The task plan chart\n\n```mermaid\nstateDiagram-v2\n" + regions[2] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-report-chart.md")))
                .isEqualTo("# The task report chart\n\n```mermaid\nstateDiagram-v2\n" + regions[3] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-turn-chart.md")))
                .isEqualTo("# The task turn chart\n\n```mermaid\nstateDiagram-v2\n" + regions[4] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-deploy-chart.md")))
                .isEqualTo("# The task deploy chart\n\n```mermaid\nstateDiagram-v2\n" + regions[5] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-session-chart.md")))
                .isEqualTo("# The task session chart\n\n```mermaid\nstateDiagram-v2\n" + regions[6] + "```\n");
    }
}
