package dev.jagt.orchestrator.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pinned by the bug it was written for: the shipped default was {@code /opt/homebrew/bin/tmux}, so every task
 * on Linux failed at "Failed to start command" before the agent ever started.
 */
class ExecutablesTest {

    private static java.util.function.Predicate<Path> present(String... paths) {
        Set<String> existing = Set.of(paths);
        return path -> existing.contains(path.toString());
    }

    @Test
    void prefersPathBecauseThatIsWhatMakesItPortable() {
        assertThat(Executables.resolve("tmux", "/opt/custom/bin:/usr/bin",
                present("/opt/custom/bin/tmux", "/usr/bin/tmux")))
                .isEqualTo("/opt/custom/bin/tmux");
    }

    /** A GUI-launched process has neither Homebrew prefix on PATH — the reason an absolute path was hardcoded. */
    @Test
    void fallsBackToTheKnownInstallDirsWhenPathHasNothing() {
        assertThat(Executables.resolve("tmux", "/usr/sbin", present("/opt/homebrew/bin/tmux")))
                .isEqualTo("/opt/homebrew/bin/tmux");
        assertThat(Executables.resolve("tmux", "", present("/usr/bin/tmux")))
                .isEqualTo("/usr/bin/tmux");
    }

    @Test
    void keepsAnExplicitPathExactlyAsConfiguredEvenIfItIsNotThereYet() {
        assertThat(Executables.resolve("/opt/nonstandard/tmux", "/usr/bin", present("/usr/bin/tmux")))
                .isEqualTo("/opt/nonstandard/tmux");
    }

    /** Found nowhere: the message a human then reads must name what they asked for, not a guessed location. */
    @Test
    void returnsTheBareNameWhenNothingHasIt() {
        assertThat(Executables.resolve("tmux", "/usr/bin", present())).isEqualTo("tmux");
    }

    @Test
    void leavesAnUnsetCommandUnset() {
        assertThat(Executables.resolve(null, "/usr/bin", present("/usr/bin/tmux"))).isEmpty();
        assertThat(Executables.resolve("  ", "/usr/bin", present("/usr/bin/tmux"))).isEmpty();
    }
}
