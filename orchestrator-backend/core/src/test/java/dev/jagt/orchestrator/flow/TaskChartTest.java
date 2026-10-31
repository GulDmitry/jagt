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
                entry("turn", "CLOSED"), entry("deploy", "NONE"), entry("git", "UNREAD"), entry("repo", "NONE"), entry("request", "NONE"), entry("checks", "NONE"), entry("approval", "UNREAD"),
                entry("infra", "NONE"), entry("qa", "NONE"), entry("session", "NONE"));
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
                .isEqualTo("# The task session chart\n\n```mermaid\nstateDiagram-v2\n" + regions[13] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-git-chart.md")))
                .isEqualTo("# The task git chart\n\n```mermaid\nstateDiagram-v2\n" + regions[6] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-repo-chart.md")))
                .isEqualTo("# The task repo chart\n\n```mermaid\nstateDiagram-v2\n" + regions[7] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-request-chart.md")))
                .isEqualTo("# The task request chart\n\n```mermaid\nstateDiagram-v2\n" + regions[8] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-checks-chart.md")))
                .isEqualTo("# The task checks chart\n\n```mermaid\nstateDiagram-v2\n" + regions[9] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-approval-chart.md")))
                .isEqualTo("# The task approval chart\n\n```mermaid\nstateDiagram-v2\n" + regions[10] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-qa-chart.md")))
                .isEqualTo("# The task qa chart\n\n```mermaid\nstateDiagram-v2\n" + regions[12] + "```\n");
        assertThat(Files.readString(Path.of("../../docs/task-infra-chart.md")))
                .isEqualTo("# The task infra chart\n\n```mermaid\nstateDiagram-v2\n" + regions[11] + "```\n");
    }
}
