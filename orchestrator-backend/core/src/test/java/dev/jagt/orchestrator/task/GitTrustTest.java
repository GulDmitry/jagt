package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitTrustTest {

    @Test
    void namesEveryEntryThatDiffersAndHow() {
        GitTrust trusted = GitTrust.of(Map.of("core.editor in /r/.git/config", "a1",
                "/r/.git/hooks/pre-push", "b2", "user.name in /r/.git/config", "c3"));

        assertThat(trusted.differences(Map.of("core.editor in /r/.git/config", "a9",
                "core.fsmonitor in /r/.git/config", "d4", "user.name in /r/.git/config", "c3")))
                .containsExactly("/r/.git/hooks/pre-push removed", "core.editor in /r/.git/config changed",
                        "core.fsmonitor in /r/.git/config added");
    }

    @Test
    void findsNothingWhereGitObeysWhatWasTrusted() {
        GitTrust trusted = GitTrust.of(Map.of("core.editor in /r/.git/config", "a1"));

        assertThat(trusted.differences(Map.of("core.editor in /r/.git/config", "a1"))).isEmpty();
    }
}
