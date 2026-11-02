package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VendorNamesTest {

    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(claude|anthropic|codex|openai|gemini|tmux|kitty|"
            + "iterm2?|wezterm|ghostty|warp|intellij|osascript|gitlab|github|glab|gh|bitbucket|gitea|jira|gradle|gradlew|"
            + "maven|mvn|npm|fable|opus|sonnet|haiku)\\b");

    private static final Map<String, String> ALLOWED = Map.of(
            "usecase/src/main/java/dev/jagt/orchestrator/job/RunningJarWatch.java", "names jagt's own build tool; iteration 2",
            "usecase/src/main/java/dev/jagt/orchestrator/service/WorktreeSandbox.java", "build tools' cache directories; iteration 2",
            "usecase/src/main/java/dev/jagt/orchestrator/service/WorktreeNoise.java", "a build tool's directory; iteration 2",
            "usecase/src/main/java/dev/jagt/orchestrator/service/ProjectSetup.java", "build tool detection; iteration 2",
            "usecase/src/main/java/dev/jagt/orchestrator/service/ConfigService.java", "a model alias as a default; iteration 3",
            "usecase/src/main/java/dev/jagt/orchestrator/service/ToolGate.java", "the tool-gate names the host CLIs; iteration 3",
            "usecase/src/main/java/dev/jagt/orchestrator/service/HostCliLine.java", "the tool-gate names the host CLIs; iteration 3");

    @Test
    void namesNoVendorOfAnAgentTerminalEditorCodeHostTrackerBuildToolOrModelInCodeOutsideTheAdapters() {
        List<String> named = namingFiles()
                .filter(file -> !ALLOWED.containsKey(file.toString()))
                .flatMap(file -> {
                    List<String> lines = lines(file);
                    return IntStream.range(0, lines.size())
                            .filter(i -> names(lines.get(i)))
                            .mapToObj(i -> file + ":" + (i + 1));
                })
                .toList();

        assertThat(named).isEmpty();
    }

    @Test
    void keepsNoAllowedFileThatNoLongerNamesAVendor() {
        List<String> naming = namingFiles().map(Path::toString).toList();

        assertThat(ALLOWED.keySet()).isSubsetOf(naming);
    }

    private static Stream<Path> namingFiles() {
        return Stream.of("core", "usecase", "surface")
                .map(module -> Path.of(module, "src/main/java"))
                .flatMap(VendorNamesTest::javaFilesUnder)
                .filter(file -> lines(file).stream().anyMatch(VendorNamesTest::names));
    }

    private static boolean names(String line) {
        return !line.strip().startsWith("*") && !line.strip().startsWith("/*")
                && VENDOR.matcher(line.replaceAll("//.*$", "")).find();
    }

    private static Stream<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> lines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
