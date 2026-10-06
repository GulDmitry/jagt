package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class VendorNamesTest {

    private static final Pattern VENDOR = Pattern.compile("(?i)\\b(claude|anthropic|codex|openai|gemini|tmux|kitty|"
            + "iterm2?|wezterm|ghostty|warp|intellij|osascript)\\b");

    @Test
    void namesNoAgentTerminalOrEditorVendorInCodeOutsideTheAdapters() {
        List<String> named = Stream.of("core", "usecase", "surface")
                .map(module -> Path.of(module, "src/main/java"))
                .flatMap(VendorNamesTest::javaFilesUnder)
                .flatMap(file -> {
                    List<String> lines = lines(file);
                    return IntStream.range(0, lines.size())
                            .filter(i -> !lines.get(i).strip().startsWith("*") && !lines.get(i).strip().startsWith("/*")
                                    && VENDOR.matcher(lines.get(i).replaceAll("//.*$", "")).find())
                            .mapToObj(i -> file + ":" + (i + 1));
                })
                .toList();

        assertThat(named).isEmpty();
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
