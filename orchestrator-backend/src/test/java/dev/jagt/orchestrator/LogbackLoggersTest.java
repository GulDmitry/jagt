package dev.jagt.orchestrator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

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
}
