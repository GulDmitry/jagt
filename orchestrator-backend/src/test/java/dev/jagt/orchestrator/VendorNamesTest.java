package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VendorNamesTest {

    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(claude|anthropic|codex|openai|gemini|tmux|kitty|"
            + "iterm2?|wezterm|ghostty|warp|intellij|osascript|gitlab|github|glab|gh|bitbucket|gitea|jira|gradle|gradlew|"
            + "maven|mvn|npm|fable|opus|sonnet|haiku)\\b");

    private static final String JOB = "usecase/src/main/java/dev/jagt/orchestrator/job/";
    private static final String SERVICE = "usecase/src/main/java/dev/jagt/orchestrator/service/";

    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry(JOB + "RunningJarWatch.java gradlew", "names jagt's own build tool; iteration 2"),
            Map.entry(SERVICE + "WorktreeSandbox.java gradle", "build tools' cache directories; iteration 2"),
            Map.entry(SERVICE + "WorktreeSandbox.java npm", "build tools' cache directories; iteration 2"),
            Map.entry(SERVICE + "WorktreeNoise.java gradle", "a build tool's directory; iteration 2"),
            Map.entry(SERVICE + "ConfigService.java sonnet", "a model alias as a default; iteration 3"),
            Map.entry(SERVICE + "ToolGate.java gh", "the tool-gate names the host CLIs; iteration 3"),
            Map.entry(SERVICE + "ToolGate.java glab", "the tool-gate names the host CLIs; iteration 3"),
            Map.entry(SERVICE + "HostCliLine.java gh", "the tool-gate names the host CLIs; iteration 3"),
            Map.entry(SERVICE + "HostCliLine.java glab", "the tool-gate names the host CLIs; iteration 3"));

    @Test
    void namesNoVendorOfAnAgentTerminalEditorCodeHostTrackerBuildToolOrModelInCodeOutsideTheAdapters() {
        List<String> named = namings()
                .filter(naming -> !ALLOWED.containsKey(naming.key()))
                .map(Naming::where)
                .toList();

        assertThat(named).isEmpty();
    }

    @Test
    void keepsNoAllowedNamingThatTheFileNoLongerHolds() {
        List<String> held = namings().map(Naming::key).distinct().toList();

        assertThat(ALLOWED.keySet()).isSubsetOf(held);
    }

    private record Naming(Path file, int line, String word) {

        String key() {
            return file + " " + word;
        }

        String where() {
            return file + ":" + line + " " + word;
        }
    }

    private static Stream<Naming> namings() {
        return Stream.of("core", "usecase", "surface")
                .map(module -> Path.of(module, "src/main/java"))
                .flatMap(VendorNamesTest::javaFilesUnder)
                .flatMap(file -> {
                    List<String> lines = lines(file);
                    return IntStream.range(0, lines.size()).boxed()
                            .flatMap(i -> words(lines.get(i)).map(word -> new Naming(file, i + 1, word)));
                });
    }

    private static Stream<String> words(String line) {
        if (line.strip().startsWith("*") || line.strip().startsWith("/*")) {
            return Stream.empty();
        }
        return VENDOR.matcher(line.replaceAll("//.*$", "")).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT));
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
