package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogbackLoggersTest {

    @Test
    void everyLoggerTheLogConfigRoutesNamesAClassThatExists() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        var named = Pattern.compile("<logger name=\"(dev\\.jagt\\.[\\w.]+)\"").matcher(config).results()
                .map(match -> match.group(1)).toList();

        assertThat(named).isNotEmpty().allSatisfy(name ->
                assertThatCode(() -> Class.forName(name)).as(name).doesNotThrowAnyException());
    }

    @Test
    void everyClassLoggingAMasterStepIsRoutedToTheMasterWindow() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        List<String> logging;
        try (Stream<Path> sources = Stream.of("src", "core/src", "usecase/src", "adapter/src", "surface/src")
                .map(root -> Path.of(root, "main/java")).filter(Files::isDirectory)
                .flatMap(LogbackLoggersTest::walk)) {
            logging = sources.filter(source -> read(source).contains("setMessage(\"master "))
                    .map(source -> source.toString().replaceFirst(".*main/java/", "")
                            .replace(".java", "").replace('/', '.'))
                    .toList();
        }

        assertThat(logging).isNotEmpty().allSatisfy(name ->
                assertThat(config).as(name).contains("<logger name=\"" + name + "\"><appender-ref ref=\"MASTER\"/>"));
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root).filter(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
