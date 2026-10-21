package dev.jagt.orchestrator.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    void findsALauncherInAPerUserScriptDirectory() {
        assertThat(Executables.resolve("idea", "/usr/bin", "/Users/me",
                path -> path.toString()
                        .equals("/Users/me/Library/Application Support/JetBrains/Toolbox/scripts/idea")))
                .isEqualTo("/Users/me/Library/Application Support/JetBrains/Toolbox/scripts/idea");
    }

    @Test
    void keepsAnExplicitPathExactlyAsConfiguredEvenIfItIsNotThereYet() {
        assertThat(Executables.resolve("/opt/nonstandard/tmux", "/usr/bin",
                path -> path.toString().equals("/usr/bin/tmux"))).isEqualTo("/opt/nonstandard/tmux");
    }

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

    @Test
    void findsALauncherInsideABundleInstalledForTheUserAlone(@TempDir Path home) throws IOException {
        Files.createDirectories(home.resolve("Applications").resolve("Notes.app"));
        Path bundle = Files.createDirectories(home.resolve("Applications").resolve("IntelliJ IDEA.app"));

        assertThat(Executables.resolve("idea", "/usr/bin", home.toString(),
                path -> path.equals(bundle.resolve("Contents").resolve("MacOS").resolve("idea"))))
                .isEqualTo(bundle.resolve("Contents").resolve("MacOS").resolve("idea").toString());
    }

    @Test
    void looksForALauncherInApplicationBundlesAndInNoOtherDirectory(@TempDir Path home) throws IOException {
        Files.createDirectories(home.resolve("Applications").resolve("Stray"));

        assertThat(Executables.resolve("idea", "/usr/bin", home.toString(),
                path -> path.startsWith(home) && path.endsWith(Path.of("Contents", "MacOS", "idea"))))
                .isEqualTo("idea");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void searchesNoUserDirectoryWhereTheHomeIsUnknown(String home) {
        assertThat(Executables.resolve("idea", "/usr/bin", home, path -> false))
                .isEqualTo("idea");
    }
}
