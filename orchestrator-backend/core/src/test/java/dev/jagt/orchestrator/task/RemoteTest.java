package dev.jagt.orchestrator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL, origin", "'  ', origin", "upstream, upstream", "none, none", "NONE, none"})
    void namesOriginUnlessTheProjectNamesAnotherRemoteOrNone(String configured, String name) {
        assertThat(Remote.of(configured).name()).isEqualTo(name);
    }

    @Test
    void holdsABranchAsTheRemotesTrackingRefWhereThereIsARemote() {
        Remote upstream = Remote.of("upstream");

        assertThat(upstream.track("main")).isEqualTo("upstream/main");
        assertThat(upstream.trackingRef("main")).isEqualTo("refs/remotes/upstream/main");
        assertThat(upstream.pushTarget()).isEqualTo("upstream");
    }

    @Test
    void holdsABranchAsTheLocalBranchAndPushesToTheRepositoryItselfWhereThereIsNoRemote() {
        assertThat(Remote.LOCAL.track("main")).isEqualTo("main");
        assertThat(Remote.LOCAL.trackingRef("main")).isEqualTo("refs/heads/main");
        assertThat(Remote.LOCAL.pushTarget()).isEqualTo(".");
    }

    @ParameterizedTest
    @CsvSource({"origin, origin/main, main", "origin, refs/remotes/origin/main, main", "origin, +refs/heads/main, main",
            "upstream, upstream/release/1, release/1", "upstream, origin/main, origin/main", "none, main, main"})
    void readsTheBranchAConfiguredRefNamesWithItsOwnRemotesPrefixDropped(String remote, String ref, String branch) {
        assertThat(Remote.of(remote).branchOf(ref)).isEqualTo(branch);
    }
}
