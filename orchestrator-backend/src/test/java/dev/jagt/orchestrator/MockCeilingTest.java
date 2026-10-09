package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MockCeilingTest {

    private static final Pattern A_TEST = Pattern.compile("@(Parameterized)?Test\\b");
    private static final Pattern A_MOCK = Pattern.compile(
            "mock\\(\\s*([A-Z]\\w*)\\.class|@Mock(?:itoBean)?\\b[^;]*?\\b([A-Z]\\w*)(?:<[^>]*>)?\\s+\\w+\\s*[;=]");

    @Test
    void aTestWiresThreeMocksAtMost() {
        assertThat(unitTests().flatMap(MockCeilingTest::testsOverTheCeiling)).isEmpty();
    }

    @Test
    void readsEveryUnitSuiteItClaimsToCheck() {
        assertThat(unitTests().count()).isGreaterThan(200);
    }

    private static Stream<String> testsOverTheCeiling(Path test) {
        String[] parts = A_TEST.split(read(test));
        Set<String> shared = mocked(parts[0]);
        return Arrays.stream(parts).skip(1)
                .map(body -> Stream.concat(shared.stream(), mocked(body).stream())
                        .collect(Collectors.toCollection(TreeSet::new)))
                .filter(mocks -> mocks.size() > 3)
                .map(mocks -> test.getFileName() + " " + mocks).distinct();
    }

    private static Set<String> mocked(String source) {
        return A_MOCK.matcher(source).results()
                .map(match -> match.group(1) != null ? match.group(1) : match.group(2))
                .collect(Collectors.toSet());
    }

    private static Stream<Path> unitTests() {
        return Stream.of("core", "usecase", "adapter", "surface", ".")
                .map(module -> Path.of(module, "src/test/java"))
                .flatMap(MockCeilingTest::javaFilesUnder)
                .filter(path -> path.getFileName().toString().endsWith("Test.java"));
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
