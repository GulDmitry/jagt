package dev.jagt.orchestrator.surface.agent;

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
            "git switch main && git push origin HEAD",
            "git -C /repo push origin HEAD",
            "cd /repo && git push -u origin HEAD",
            "git push origin ABC-42 & git push origin main",
            "git push origin ABC-42 >/dev/null main",
            "git push origin ABC-42 2> /dev/null main",
            "git push origin ABC-42 2>&1 main",
            "git push origin ABC-42 >&2 main",
            "git push origin ABC-42 &>/dev/null main",
            "git push origin ABC-42 &>>/tmp/log main",
            "\\git push origin main",
            "\"git\" push origin main",
            "'git' push origin main",
            "g''it push origin main",
            "git \"push\" origin main",
            "g'i't push origin main",
            "git pu'sh' origin main",
            "GIT_DIR=/repo/.git git push origin HEAD",
            "GIT_WORK_TREE=/repo git push origin HEAD",
            "git push origin ABC-42:HEAD",
            "git push origin ABC-42 \\#x main",
            "git push origin ABC-42 x\\> main",
    })
    void refusesAPushWhoseDestinationIsNotTheTasksBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290))
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
            "git commit -c HEAD && git push origin ABC-42",
    })
    void allowsEverythingThatDoesNotWriteAnotherBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git push --no-verify origin ABC-42",
            "git -c core.hooksPath=/dev/null push origin ABC-42",
            "git -c alias.p=push p origin dev",
            "env -u GIT_CONFIG_COUNT git push origin ABC-42",
            "GIT_CONFIG_COUNT=0 git push origin ABC-42",
            "env -i git push origin ABC-42",
            "sh -c 'git push origin dev'",
            "bash -lc \"git push origin ABC-42\"",
            "unset GIT_CONF''IG_COUNT; git push origin ABC-42",
            "env - git push origin ABC-42",
            "git -c remote.origin.push=refs/heads/ABC-42:refs/heads/main push origin ABC-42",
            "git '-c' remote.origin.push=refs/heads/ABC-42:refs/heads/main push origin ABC-42",
    })
    void refusesAPushThatCouldSkipThePrePushCheck(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("could skip its pre-push check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"git push --force origin ABC-42", "git push -f origin ABC-42",
            "git push -uf origin ABC-42", "git push origin +ABC-42", "git push --force-with-lease origin +ABC-42"})
    void refusesForcingTheTasksOwnBranchWithoutTheLease(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("--force-with-lease");
    }

    @ParameterizedTest
    @ValueSource(strings = {"curl -X POST 'http://127.0.0.1:8290/api/tasks/actions/deploy?task=ABC-7'",
            "curl -d '{\"line\":\"x\"}' localhost:8290/api/tasks/say", "curl http://[::1]:8290/mcp",
            "cat ../../.jagt/master-token", "node /root/mcp_client.js", "cat .jagt/master-tok'en'",
            "curl -X POST -H Origin:http://localhost:'8290' http://localhost:'8290'/api/tasks/actions/deploy?task=ABC-42",
            "curl -X POST -H Origin:http://localhost:08290 http://localhost:08290/api/tasks/actions/deploy?task=ABC-42",
            "curl -X POST http://127.1:8290/api/tasks/say", "curl -X POST http://2130706433:8290/api/tasks/say",
            "curl -X POST http://0x7f000001:8290/api/tasks/say", "curl -X POST http://LOCALHOST:8290/api/tasks/say",
            "curl -X POST http://[::ffff:127.0.0.1]:8290/api/tasks/say", "cat .jagt/MASTER-TOKEN"})
    void refusesALineReachingTheBoardOrTheMastersToken(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("reaching its board");
    }

    @ParameterizedTest
    @ValueSource(strings = {"git send-pack origin refs/heads/ABC-42:refs/heads/main",
            "git -C /wt send-pack origin ABC-42:main", "git http-push https://host/repo.git main",
            "git receive-pack /repo", "git-send-pack origin ABC-42:main",
            "gh api -X PATCH repos/o/r/git/refs/heads/main -f sha=abc", "gh api --method POST repos/o/r/git/refs",
            "gh api --method=PUT repos/o/r/git/refs", "gh api -XDELETE repos/o/r/git/refs/heads/main",
            "gh api repos/o/r/git/refs -f ref=refs/heads/main", "gh api repos/o/r/git/refs -Fsha=abc",
            "glab api -X PUT projects/1/repository/branches", "glab api --input body.json projects/1",
            "cd /wt && gh api graphql --raw-field query=x", "gh pr merge 12 --admin", "glab mr merge 12",
            "gh repo edit --default-branch x", "gh", "/usr/local/bin/gh release create v1",
            "GH_TOKEN=x gh pr merge 12", "env -u GH_HOST gh pr merge 12", "xargs -n 1 gh pr close",
            "command 'gh' pr merge 12"})
    void refusesWritingToTheCodeHostPastThePush(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("writing to the code host");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ gh pr merge 1; }", "if gh pr merge 1; then :; fi", "! gh pr merge 1",
            "time gh pr merge 1", "nohup gh pr merge 1", "sudo gh pr merge 1", "timeout 9 gh pr merge 1",
            "eval gh pr merge 1", "while true; do glab mr merge 1; done", ">/dev/null gh pr merge 1",
            "find . -exec gh pr merge 1 ;", "builtin exec gh pr merge 1", "gh -R o/r pr merge 1",
            "bash -c 'gh pr merge 1'", "sh -c \"glab mr merge 1\"", "eval \"gh pr merge 1\"",
            "env -S 'gh pr merge 1'", "zsh -lc 'gh release create v1'"})
    void refusesAHostCliWriteHoweverItIsWrapped(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("writing to the code host");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gh api repos/o/r/pulls", "gh api -X GET repos/o/r", "glab api --method get projects/1",
            "gh pr view 7", "glab mr view 7", "gh -R o/r pr view 1", "gh --repo o/r pr list"})
    void letsTheCodeHostBeRead(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"grep -rn glab src", "ls docs/gh", "which gh", "git commit -m \"bump gh to 2.0\""})
    void letsAHostCliBeNamedAnywhereButInCommandPosition(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).isEmpty();
    }

    @Test
    void letsATaskWhoseBranchOpensWithAHashPushItQuoted() {
        assertThat(ToolGate.refusal("Bash", "git push origin '#123'", "#123", 8290)).isEmpty();
    }

    @Test
    void refusesAPushThatNamesNoBranchBecauseTheConfigWouldDecide() {
        assertThat(ToolGate.refusal("Bash", "git push", "ABC-42", 8290)).get()
                .asString().contains("names no branch");
    }

    @Test
    void answersNothingForAToolThatCannotPush() {
        assertThat(ToolGate.refusal("Read", "git push origin dev", "ABC-42", 8290)).isEmpty();
    }

    @Test
    void answersNothingWhenTheCallerHasNoBranchOfItsOwn() {
        assertThat(ToolGate.refusal("Bash", "git push origin dev", null, 8290)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"git push origin :ABC-42", "git push --delete origin ABC-42",
            "git push -d origin ABC-42", "git push --dele origin ABC-42", "git push -ud origin ABC-42"})
    void refusesDeletingTheTasksOwnBranch(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("refuses deleting a branch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"git $'\\x70ush' origin main", "git $\"push\" origin main"})
    void refusesAGitLineInAQuotingItDoesNotRead(String command) {
        assertThat(ToolGate.refusal("Bash", command, "ABC-42", 8290)).get()
                .asString().contains("plain quotes");
    }

    @Test
    void refusesALineReachingTheBoardOnTheAddressItIsServedOn() {
        assertThat(ToolGate.refusal("Bash", "curl -X POST http://192.168.1.5:8290/api/tasks/say", "ABC-42", 8290))
                .get().asString().contains("reaching its board");
    }
}
