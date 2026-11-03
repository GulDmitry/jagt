package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FailureCausesTest {

    @Test
    void noLogLineTakesItsCauseFromAMessageThatMayBeNull() throws IOException {
        Pattern messageAsCause = Pattern.compile("addKeyValue\\(\\s*\"cause\",[^;]*?\\.getMessage\\(\\)");

        try (Stream<Path> sources = Files.walk(Path.of("."))) {
            assertThat(sources.filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> messageAsCause.matcher(read(path)).find())).isEmpty();
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
