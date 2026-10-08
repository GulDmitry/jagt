package dev.jagt.orchestrator;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigReferenceTest {

    private static final List<Path> ROOTS = List.of(Path.of("usecase/src/main/java"), Path.of("adapter/src/main/java"),
            Path.of("surface/src/main/java"), Path.of("src/main/java"));

    @Test
    void jagtYmlDistDescribesEveryKeyTheBackendReads() {
        String dist = read(Path.of("../jagt.yml.dist"));
        Pattern named = Pattern.compile("[\"{]orchestrator\\.([a-z][a-z-]*)");
        Stream<String> bound = Stream.of(OrchestratorProperties.class, AssistantProperties.class)
                .flatMap(type -> Arrays.stream(type.getRecordComponents())).map(RecordComponent::getName);
        Stream<String> looked = ROOTS.stream().flatMap(ConfigReferenceTest::javaFilesUnder)
                .flatMap(path -> named.matcher(read(path)).results()).map(match -> Pattern.compile("-([a-z])").matcher(match.group(1))
                        .replaceAll(letter -> letter.group(1).toUpperCase(Locale.ROOT)));

        assertThat(Stream.concat(bound, looked).distinct()
                .filter(key -> !Pattern.compile("(?m)^\\s*#?\\s*" + key + ":").matcher(dist).find())).isEmpty();
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
