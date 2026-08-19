package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dependency rule, asserted rather than promised: an import that points outward compiles and works, and only
 * rots the design. Until the rings are separate build modules and the compiler answers this, these three
 * assertions are what stops the direction being lost one convenience at a time.
 */
class RingsTest {

    /**
     * Every module's production sources. A root that stops matching after a refactor is how this test goes green
     * and stops guarding anything, so it is asserted below rather than assumed.
     */
    private static final List<Path> ROOTS = List.of(
            Path.of("core/src/main/java/dev/jagt/orchestrator"),
            Path.of("usecase/src/main/java/dev/jagt/orchestrator"),
            Path.of("adapter/src/main/java/dev/jagt/orchestrator"),
            Path.of("surface/src/main/java/dev/jagt/orchestrator"),
            Path.of("src/main/java/dev/jagt/orchestrator"));
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
    @ValueSource(strings = {"capability", "command", "job", "notify", "service", "surface", "config", "startup"})
    void nothingBetweenTheCentreAndTheEdgeNamesTheEdge(String ring) {
        assertThat(importsOf(ring).filter("adapter"::equals)).isEmpty();
    }

    /** A framework in the centre would make every rule above it need a container to be exercised. */
    @ParameterizedTest
    @ValueSource(strings = {"flow", "task", "port"})
    void theCentreCarriesNoFrameworkAtAll(String ring) {
        assertThat(sources(ring).filter(text -> text.contains("org.springframework")
                || text.contains("lombok"))).isEmpty();
    }

    /**
     * The guard on the guard: a ring whose sources this cannot find is a ring nothing checks, and the failure mode
     * is silence. Every assertion below reads files, so it must be able to prove it read some.
     */
    @Test
    void readsEveryRingItClaimsToCheck() {
        assertThat(ROOTS).allSatisfy(root -> assertThat(Files.isDirectory(root))
                .describedAs("source root %s", root).isTrue());
        assertThat(List.of("flow", "task", "port", "capability", "command", "job", "notify", "service",
                "surface", "adapter", "config", "startup")).allSatisfy(ring ->
                assertThat(sources(ring).count()).describedAs("java files in %s", ring).isPositive());
    }

    /**
     * The whole point of the edge: porting to another machine is a folder, not a search. {@code open} is not on
     * the list — it is an ordinary English word in the sentences jagt writes.
     */
    @Test
    void theOperatingSystemIsNamedOnlyAtTheEdge() {
        List<String> osOnly = List.of("osascript", "notify-send", "setsid", "/opt/homebrew", "/usr/local/bin",
                "wt.exe", "PATHEXT", "lsof", "kill -9", "/Applications");

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
