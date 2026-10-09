package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KindsTest {

    private static final List<Path> ROOTS = List.of(
            Path.of("core/src/main/java/dev/jagt/orchestrator"),
            Path.of("usecase/src/main/java/dev/jagt/orchestrator"),
            Path.of("adapter/src/main/java/dev/jagt/orchestrator"),
            Path.of("surface/src/main/java/dev/jagt/orchestrator"),
            Path.of("src/main/java/dev/jagt/orchestrator"));
    private static final Set<String> USE_CASES = Set.of("capability", "command", "job", "notify", "startup", "service");
    private static final Set<String> FACES = Set.of("Jobs", "Notifications");

    @Test
    void aServiceClassOneKindAloneReadsLivesInThatKind() {
        Map<Path, String> code = code();

        assertThat(code.keySet().stream().filter(file -> kind(file).equals("service"))
                .filter(file -> {
                    Set<String> readers = readers(file, code);
                    return readers.size() == 1 && !readers.contains("service") && !readers.contains("surface");
                })
                .map(file -> name(file) + " " + readers(file, code))).isEmpty();
    }

    @Test
    void anotherUseCaseReadsAKindOnlyThroughItsRegistry() {
        Map<Path, String> code = code();

        assertThat(code.keySet().stream().filter(file -> USE_CASES.contains(kind(file)) && !kind(file).equals("service"))
                .filter(file -> !FACES.contains(name(file)))
                .filter(file -> readers(file, code).stream().map(reader -> reader.split("/")[0])
                        .anyMatch(reader -> USE_CASES.contains(reader) && !reader.equals(kind(file))))
                .map(file -> name(file) + " " + readers(file, code))).isEmpty();
    }

    @Test
    void readsEveryKindItJudges() {
        assertThat(code().keySet().stream().map(KindsTest::kind).collect(Collectors.toSet()))
                .containsAll(USE_CASES).contains("service/master", "surface");
    }

    private static Set<String> readers(Path file, Map<Path, String> code) {
        Pattern named = Pattern.compile("\\b" + name(file) + "\\b");
        return code.entrySet().stream().filter(other -> !other.getKey().equals(file))
                .filter(other -> named.matcher(other.getValue()).find())
                .map(other -> kind(other.getKey())).filter(kind -> !kind.equals("root"))
                .collect(Collectors.toSet());
    }

    private static String kind(Path file) {
        String[] packages = file.toString().split("dev/jagt/orchestrator/")[1].split("/");
        if (packages.length == 1) {
            return "root";
        }
        return packages[0].equals("service") && packages.length > 2 ? "service/" + packages[1] : packages[0];
    }

    private static String name(Path file) {
        return file.getFileName().toString().replace(".java", "");
    }

    private static Map<Path, String> code() {
        return ROOTS.stream().flatMap(KindsTest::javaFilesUnder).collect(Collectors.toMap(file -> file,
                file -> read(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "")));
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
