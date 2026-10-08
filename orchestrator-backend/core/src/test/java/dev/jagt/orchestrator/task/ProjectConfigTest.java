package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "main        | refs/heads/main",
            "origin/main | refs/heads/main",
            "main        | origin/main",
            "main        | refs/remotes/origin/main",
            "main        | +refs/heads/main",
            "main        | ' main '",
            "refs/heads/main | main"
    })
    void deploysIntoTheBaseBranchWhenBothNameItInAnotherSpelling(String base, String deploy) {
        var project = new ProjectConfig("/repos/parser", base, deploy, List.of());

        assertThat(project.deploysIntoTheBaseBranch()).isTrue();
    }
}
