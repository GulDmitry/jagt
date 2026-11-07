package dev.jagt.orchestrator.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitTrustTest {

  @Test
  void namesEveryEntryThatDiffersAndHow() {
    GitTrust trusted =
        GitTrust.of(
            Map.of(
                "core.editor in /r/.git/config",
                "a1",
                "/r/.git/hooks/pre-push",
                "b2",
                "user.name in /r/.git/config",
                "c3"));

    assertThat(
            trusted.differences(
                Map.of(
                    "core.editor in /r/.git/config",
                    "a9",
                    "core.fsmonitor in /r/.git/config",
                    "d4",
                    "user.name in /r/.git/config",
                    "c3")))
        .containsExactly(
            "/r/.git/hooks/pre-push removed",
            "core.editor in /r/.git/config changed",
            "core.fsmonitor in /r/.git/config added");
  }

  @Test
  void findsNothingWhereGitObeysWhatWasTrusted() {
    GitTrust trusted = GitTrust.of(Map.of("core.editor in /r/.git/config", "a1"));

    assertThat(trusted.differences(Map.of("core.editor in /r/.git/config", "a1"))).isEmpty();
  }

  @Test
  void dropsTheNamedEntriesAndKeepsTheRest() {
    GitTrust trusted =
        GitTrust.of(
            Map.of("core.editor in /a/.git/config", "a1", "core.bare in /b/.git/config", "b2"));

    assertThat(trusted.excluding(Set.of("core.bare in /b/.git/config")).recorded())
        .containsOnlyKeys("core.editor in /a/.git/config");
  }
}
