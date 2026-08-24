package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pinned by the bug it was written for: the shipped default was {@code /opt/homebrew/bin/tmux}, so every task
 * on Linux failed at "Failed to start command" before the agent ever started.
 */
class ExecutablesTest {

    @Test
    void answersForABareSpawnByLookingAtPathAndNowhereElse() {
        assertThat(Executables.onPath("git", "/usr/bin", path -> path.toString().equals("/usr/bin/git")))
                .isTrue();
        assertThat(Executables.onPath("git", "/usr/sbin",
                path -> path.toString().equals("/opt/homebrew/bin/git"))).isFalse();
    }

    @Test
    void prefersPathBecauseThatIsWhatMakesItPortable() {
        assertThat(Executables.resolve("tmux", "/opt/custom/bin:/usr/bin",
                path -> Set.of("/opt/custom/bin/tmux", "/usr/bin/tmux").contains(path.toString())))
                .isEqualTo("/opt/custom/bin/tmux");
    }

    /** A GUI-launched process has neither Homebrew prefix on PATH — the reason an absolute path was hardcoded. */
    @Test
    void fallsBackToTheHomebrewPrefixWhenPathHasNothing() {
        assertThat(Executables.resolve("tmux", "/usr/sbin",
                path -> path.toString().equals("/opt/homebrew/bin/tmux")))
                .isEqualTo("/opt/homebrew/bin/tmux");
    }

    @Test
    void fallsBackToTheSystemBinDirectoryWhenPathIsNotSetAtAll() {
        assertThat(Executables.resolve("tmux", "", path -> path.toString().equals("/usr/bin/tmux")))
                .isEqualTo("/usr/bin/tmux");
    }

    @Test
    void findsADesktopLauncherInsideItsApplicationBundle() {
        assertThat(Executables.resolve("idea", "/usr/bin", "/Users/me",
                path -> path.toString().equals("/Applications/IntelliJ IDEA.app/Contents/MacOS/idea"),
                dir -> dir.toString().equals("/Applications")
                        ? List.of(Path.of("/Applications/Notes.app"), Path.of("/Applications/IntelliJ IDEA.app"))
                        : List.of()))
                .isEqualTo("/Applications/IntelliJ IDEA.app/Contents/MacOS/idea");
    }

    @Test
    void findsALauncherInAPerUserScriptDirectory() {
        assertThat(Executables.resolve("idea", "/usr/bin", "/Users/me",
                path -> path.toString()
                        .equals("/Users/me/Library/Application Support/JetBrains/Toolbox/scripts/idea"),
                dir -> List.of()))
                .isEqualTo("/Users/me/Library/Application Support/JetBrains/Toolbox/scripts/idea");
    }

    @Test
    void keepsAnExplicitPathExactlyAsConfiguredEvenIfItIsNotThereYet() {
        assertThat(Executables.resolve("/opt/nonstandard/tmux", "/usr/bin",
                path -> path.toString().equals("/usr/bin/tmux"))).isEqualTo("/opt/nonstandard/tmux");
    }

    /** Found nowhere: the message a human then reads must name what they asked for, not a guessed location. */
    @Test
    void returnsTheBareNameWhenNothingHasIt() {
        assertThat(Executables.resolve("tmux", "/usr/bin", path -> false)).isEqualTo("tmux");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"  "})
    void leavesAnUnsetCommandUnset(String command) {
        assertThat(Executables.resolve(command, "/usr/bin", path -> path.toString().equals("/usr/bin/tmux")))
                .isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void countsAnUnsetCommandAsSomethingNobodyMaySpawn(String resolved) {
        assertThat(Executables.unresolved(resolved)).isTrue();
    }
}
