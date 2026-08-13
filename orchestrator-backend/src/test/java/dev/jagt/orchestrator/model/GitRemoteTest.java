package dev.jagt.orchestrator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GitRemoteTest {

    @ParameterizedTest
    @CsvSource({
        "git@example.com:group-a/backend.git, group-a/backend",
        "https://example.com/group-a/backend.git, group-a/backend",
        "https://example.com/group-a/backend, group-a/backend",
        "ssh://git@example.com:2222/group-a/backend.git, group-a/backend"
    })
    void derivesTheProjectPathFromEveryRemoteShape(String remote, String expected) {
        assertThat(GitRemote.projectPath(remote)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "git@example.com:group-a/backend.git, example.com",
        "ssh://git@example.com:2222/group-a/backend.git, example.com",
        "https://example.com/group-a/backend.git, example.com",
        "https://user@example.com:8443/group-a/backend, example.com"
    })
    void derivesTheBareHostWithoutUserOrPort(String remote, String expected) {
        assertThat(GitRemote.host(remote)).isEqualTo(expected);
    }

    @Test
    void readsNothingOutOfABlankRemote() {
        assertThat(GitRemote.projectPath(" ")).isNull();
        assertThat(GitRemote.host(" ")).isNull();
    }
}
