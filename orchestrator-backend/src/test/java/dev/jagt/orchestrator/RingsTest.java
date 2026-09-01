package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RingsTest {

    private static final List<Path> ROOTS = List.of(
            Path.of("core/src/main/java/dev/jagt/orchestrator"),
            Path.of("usecase/src/main/java/dev/jagt/orchestrator"),
            Path.of("adapter/src/main/java/dev/jagt/orchestrator"),
            Path.of("surface/src/main/java/dev/jagt/orchestrator"),
            Path.of("src/main/java/dev/jagt/orchestrator"));
    private static final Set<String> CORE = Set.of("flow", "task", "port");

    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port"})
    void theCentreImportsNothingFromTheRingsAroundIt(String ring) {
        assertThat(importsOf(ring).filter(imported -> !CORE.contains(imported))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"capability", "command", "job", "notify", "service", "surface", "config", "startup"})
    void nothingBetweenTheCentreAndTheEdgeNamesTheEdge(String ring) {
        assertThat(importsOf(ring).filter("adapter"::equals)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port"})
    void theCentreCarriesNoFrameworkAtAll(String ring) {
        assertThat(sources(ring).filter(text -> text.contains("org.springframework")
                || text.contains("lombok"))).isEmpty();
    }

    @Test
    void readsEveryRingItClaimsToCheck() {
        assertThat(ROOTS).allSatisfy(root -> assertThat(Files.isDirectory(root))
                .describedAs("source root %s", root).isTrue());
        assertThat(List.of("flow", "task", "port", "capability", "command", "job", "notify", "service",
                "surface", "adapter", "config", "startup")).allSatisfy(ring ->
                assertThat(sources(ring).count()).describedAs("java files in %s", ring).isPositive());
    }

    @Test
    void theOperatingSystemIsNamedOnlyAtTheEdge() {
        List<String> osOnly = List.of("osascript", "notify-send", "setsid", "/opt/homebrew", "/usr/local/bin",
                "wt.exe", "PATHEXT", "lsof", "kill -9", "/Applications");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> osOnly.stream().anyMatch(name -> read(path).contains(name)))
                .map(Path::getFileName)).isEmpty();
    }

    @Test
    void theVendorIsNamedOnlyAtTheEdge() {
        List<String> vendors = List.of("claude", "codex", "qwen", "kitty", "tmux", "intellij");
        Pattern prefix = Pattern.compile("@ConfigurationProperties\\(prefix = \"([^\"]+)\"");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> vendors.stream().anyMatch(vendor ->
                        path.getFileName().toString().toLowerCase(Locale.ROOT).contains(vendor)
                                || prefix.matcher(read(path)).results()
                                .anyMatch(match -> match.group(1).toLowerCase(Locale.ROOT).contains(vendor))))
                .map(Path::getFileName)).isEmpty();
    }

    private static Stream<String> importsOf(String ring) {
        Pattern named = Pattern.compile("dev\\.jagt\\.orchestrator\\.([a-z]\\w*)");
        return sources(ring).flatMap(text -> named.matcher(text).results())
                .map(match -> match.group(1)).distinct();
    }

    private static Stream<String> sources(String ring) {
        return ROOTS.stream().map(root -> root.resolve(ring)).filter(Files::isDirectory)
                .flatMap(RingsTest::javaFilesUnder).map(RingsTest::read);
    }

    private static Stream<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
