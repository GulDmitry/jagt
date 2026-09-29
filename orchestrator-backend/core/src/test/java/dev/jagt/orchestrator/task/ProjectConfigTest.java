package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectConfigTest {

    @Test
    void tellsARouterWhatTheRepositoryIsInTheWordsTheInstallWrote() {
        var project = new ProjectConfig("/repos/parser", "origin/main", "dev", List.of(), List.of(),
                "the PDF parser behind the intake form");

        assertThat(project.aboutOrPath()).isEqualTo("the PDF parser behind the intake form");
    }

    @Test
    void fallsBackToThePathWhereNobodyWroteOne() {
        var project = new ProjectConfig("/repos/parser", "origin/main", "dev", List.of());

        assertThat(project.aboutOrPath()).isEqualTo("/repos/parser");
    }
}
