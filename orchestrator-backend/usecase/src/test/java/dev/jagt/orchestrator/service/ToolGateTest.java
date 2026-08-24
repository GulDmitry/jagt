package dev.jagt.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToolGateTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "git push origin dev",
            "git push origin HEAD:dev",
            "git push origin refs/heads/ABC-42:refs/heads/release/stage",
            "git push --force origin +HEAD:main",
            "cd /wt && git push origin dev",
            "git commit -m 'x' ; git push origin master",
            "/opt/homebrew/bin/git push origin dev",
            "git push -o ci.skip origin dev",
            "git -C /wt/ABC-42 push origin dev",
            "git --no-pager push origin main",
            "git -c user.name=x push origin release/stage",
    })
    void refusesAPushWhoseDestinationIsNotTheTasksBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42"))
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("ABC-42");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git push origin ABC-42",
            "git push origin ABC-42:ABC-42",
            "git push origin refs/heads/ABC-42:refs/heads/ABC-42",
            "git push --force-with-lease origin ABC-42",
            "git push -o ci.skip origin ABC-42",
            "git add -A && git commit -m 'ABC-42 fix' && git push origin ABC-42",
            "git fetch origin dev",
            "git status",
            "echo 'git push origin dev is what jagt refuses'",
            "git push origin ABC-42 2>&1",
            "git push origin ABC-42 > /tmp/out",
            "git push origin ABC-42 # push the branch",
            "git push origin \"ABC-42\"",
            "git push origin HEAD",
            "git push -u origin HEAD",
            "git -C /wt/ABC-42 push origin ABC-42",
    })
    void allowsEverythingThatDoesNotWriteAnotherBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42")).isEmpty();
    }

    @Test
    void letsATaskWhoseBranchOpensWithAHashPushItQuoted() {
        assertThat(ToolGate.refusal("Bash", "git push origin '#123'", "#123")).isEmpty();
    }

    @Test
    void refusesAPushThatNamesNoBranchBecauseTheConfigWouldDecide() {
        assertThat(ToolGate.refusal("Bash", "git push", "ABC-42")).get()
                .asString().contains("names no branch");
    }

    @Test
    void answersNothingForAToolThatCannotPush() {
        assertThat(ToolGate.refusal("Read", "git push origin dev", "ABC-42")).isEmpty();
    }

    @Test
    void answersNothingWhenTheCallerHasNoBranchOfItsOwn() {
        assertThat(ToolGate.refusal("Bash", "git push origin dev", null)).isEmpty();
    }

    /** The branch a review request is built on: deleting it discards what was shipped. */
    @ParameterizedTest
    @ValueSource(strings = {"git push origin :ABC-42", "git push --delete origin ABC-42",
            "git push -d origin ABC-42"})
    void refusesDeletingTheTasksOwnBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42")).get()
                .asString().contains("refuses deleting a branch");
    }
}
