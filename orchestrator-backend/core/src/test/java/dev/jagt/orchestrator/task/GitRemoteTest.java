package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        "https://u:t0ken@example.com/group-a/backend.git, https://example.com/group-a/backend.git",
        "https://u:p@ss@example.com/group-a/backend.git, https://example.com/group-a/backend.git",
        "ssh://git@example.com:2222/group-a/backend.git, ssh://git@example.com:2222/group-a/backend.git"
    })
    void dropsEveryCharacterOfAnHttpsCredential(String remote, String expected) {
        assertThat(GitRemote.withoutCredentials(remote)).isEqualTo(expected);
    }

    @Test
    void readsNothingOutOfABlankRemote() {
        assertThat(GitRemote.projectPath(" ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "nowhere"})
    void readsNothingOutOfARemoteWithNoPathPart(String remote) {
        assertThat(GitRemote.projectPath(remote)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "git@example.com:group-a/backend.git, https://example.com/group-a/backend",
        "git@example.com:group-a/backend.git, https://example.com/group-a/backend/-/merge_requests/4",
        "https://example.com/group-a/backend.git, https://example.com/group-a/backend/pull/4",
        "ssh://git@example.com:2222/group-a/backend.git, https://EXAMPLE.com/group-a/backend/pull/4"
    })
    void holdsAnAddressOnItsHostUnderItsExactPath(String remote, String address) {
        assertThat(GitRemote.holds(remote, address)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "git@example.com:group-a/backend.git, https://example.com/group-a/backend-shop/pull/4",
        "git@example.com:group-a/backend.git, https://example.com/other/group-a/backend/pull/4",
        "git@example.com:group-a/backend.git, https://other.example.com/group-a/backend/pull/4",
        "git@example.com:group-a/backend.git, not an address"
    })
    void holdsNoAddressOnAnotherHostOrAnotherPath(String remote, String address) {
        assertThat(GitRemote.holds(remote, address)).isFalse();
    }

    @Test
    void holdsAnAddressUnderTheExactPathOfAFileRemoteWhichNamesNoHost() {
        assertThat(GitRemote.holds("file:///srv/git/shop.git", "https://code.example/srv/git/shop/-/merge_requests/1")).isTrue();
        assertThat(GitRemote.holds("file:///srv/git/shop.git", "https://code.example/srv/git/shop-web/-/merge_requests/1")).isFalse();
    }
}
