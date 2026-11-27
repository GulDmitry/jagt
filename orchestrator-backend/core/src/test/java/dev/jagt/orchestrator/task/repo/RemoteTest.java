package dev.jagt.orchestrator.task.repo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RemoteTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {"NULL, origin", "'  ', origin", "upstream, upstream", "none, none", "NONE, none"})
  void namesOriginUnlessTheProjectNamesAnotherRemoteOrNone(String configured, String name) {
    assertThat(Remote.of(configured).name()).isEqualTo(name);
  }

  @Test
  void advisesPlainPushWhereTheRefusedLineHeldNoLease() {
    assertThat(Remote.ORIGIN.pushAdvice("ABC-42", null))
        .isEqualTo("Push ABC-42 with a plain `git push origin ABC-42`.");
  }

  @Test
  void advisesTheLeasePushAloneOnItsLineWhereTheRefusedLineHeldOne() {
    assertThat(Remote.ORIGIN.pushAdvice("ABC-42", "--force-with-lease=ABC-42:abc123"))
        .isEqualTo(
            "Push ABC-42 with `git push --force-with-lease=ABC-42:abc123 origin ABC-42`, alone on"
                + " its line: a rebased branch needs the lease.");
  }

  @Test
  void advisesCommittingWhereThereIsNoRemoteWhateverTheLine() {
    assertThat(Remote.LOCAL.pushAdvice("ABC-42", "--force-with-lease"))
        .isEqualTo(
            "Nothing is pushed from here: the repository stays on this machine, so commit ABC-42.");
  }

  @Test
  void holdsBranchAsTheRemotesTrackingRefWhereThereIsRemote() {
    Remote upstream = Remote.of("upstream");

    assertThat(upstream.track("main")).isEqualTo("upstream/main");
    assertThat(upstream.trackingRef("main")).isEqualTo("refs/remotes/upstream/main");
    assertThat(upstream.pushTarget()).isEqualTo("upstream");
  }

  @Test
  void holdsBranchAsTheLocalBranchAndPushesToTheRepositoryItselfWhereThereIsNoRemote() {
    assertThat(Remote.LOCAL.track("main")).isEqualTo("main");
    assertThat(Remote.LOCAL.trackingRef("main")).isEqualTo("refs/heads/main");
    assertThat(Remote.LOCAL.pushTarget()).isEqualTo(".");
  }

  @ParameterizedTest
  @CsvSource({
    "origin, origin/main, main",
    "origin, refs/remotes/origin/main, main",
    "origin, +refs/heads/main, main",
    "upstream, upstream/release/1, release/1",
    "upstream, origin/main, origin/main",
    "none, main, main"
  })
  void readsTheBranchConfiguredRefNamesWithItsOwnRemotesPrefixDropped(
      String remote, String ref, String branch) {
    assertThat(Remote.of(remote).branchOf(ref)).isEqualTo(branch);
  }
}
