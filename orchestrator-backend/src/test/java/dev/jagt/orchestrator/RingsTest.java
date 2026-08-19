package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dependency rule, asserted rather than promised: an import that points outward compiles and works, and only
 * rots the design. Until the rings are separate build modules and the compiler answers this, these three
 * assertions are what stops the direction being lost one convenience at a time.
 */
class RingsTest {

    /** The centre is its own module now, so the rule is read across both source roots. */
    private static final List<Path> ROOTS = List.of(Path.of("src/main/java/dev/jagt/orchestrator"),
            Path.of("core/src/main/java/dev/jagt/orchestrator"));
    private static final Set<String> CORE = Set.of("flow", "task", "port");

    /** The centre may know its own rings and nothing else — that is what makes its tests need no container. */
    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port"})
    void theCentreImportsNothingFromTheRingsAroundIt(String ring) {
        assertThat(importsOf(ring).filter(imported -> !CORE.contains(imported))).isEmpty();
    }

    /**
     * The edge is the outermost ring, so a use case naming one is the dependency rule backwards: it is what makes
     * an OS or a vendor impossible to swap. What the use cases need from out there they declare as a port.
     */
    @ParameterizedTest
    @ValueSource(strings = {"capability", "command", "job", "notify", "service", "surface"})
    void nothingBetweenTheCentreAndTheEdgeNamesTheEdge(String ring) {
        assertThat(importsOf(ring).filter("adapter"::equals)).isEmpty();
    }

    /** A framework in the centre would make every rule above it need a container to be exercised. */
    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port"})
    void theCentreCarriesNoFrameworkAtAll(String ring) {
        assertThat(sources(ring).filter(text -> text.contains("import org.springframework")
                || text.contains("import lombok"))).isEmpty();
    }

    /**
     * The whole point of the edge: porting to another machine is a folder, not a search. `open` is not on the
     * list — it is an ordinary English word in the sentences jagt writes.
     */
    @Test
    void theOperatingSystemIsNamedOnlyAtTheEdge() {
        List<String> osOnly = List.of("osascript", "notify-send", "setsid", "/opt/homebrew", "/usr/local/bin",
                "wt.exe", "PATHEXT");

        assertThat(ROOTS.stream().flatMap(RingsTest::javaFilesUnder)
                .filter(path -> !path.toString().contains("/adapter/"))
                .filter(path -> osOnly.stream().anyMatch(name -> read(path).contains(name)))
                .map(Path::getFileName)).isEmpty();
    }

    /**
     * Every ring the source NAMES, however it names it: a plain import, a static import, or a fully qualified
     * reference written inline. Matching import lines alone would let the same dependency in by the back door.
     */
    private static Stream<String> importsOf(String ring) {
        java.util.regex.Pattern named = java.util.regex.Pattern.compile("dev\\.jagt\\.orchestrator\\.([a-z]\\w*)");
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
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
