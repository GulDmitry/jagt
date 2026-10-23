package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SuitesTapNobodyTest {

    @Test
    void everyBootedSuiteMocksTheDesktopNotifierButTheOnePinningItsWiring() throws IOException {
        List<String> tapping;
        try (Stream<Path> sources = Files.walk(Path.of("src"))) {
            tapping = sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).matches("(?sm).*^@SpringBootTest.*"))
                    .filter(path -> !read(path).matches("(?s).*@MockitoBean\\s+(private\\s+)?UserNotifier\\b.*"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.equals("LinuxProfileContextTest.java"))
                    .sorted().toList();
        }

        assertThat(tapping).isEmpty();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
