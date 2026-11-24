package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LogbackLoggersTest {

  @Test
  void everyLoggerTheLogConfigRoutesNamesClassOrPackageThatExists() throws IOException {
    String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

    var named =
        Pattern.compile("<logger name=\"(dev\\.jagt\\.[\\w.]+)\"")
            .matcher(config)
            .results()
            .map(match -> match.group(1))
            .toList();

    assertThat(named)
        .isNotEmpty()
        .allSatisfy(name ->
            assertThat(
                    SourceRoots.mainJavaFiles()
                        .anyMatch(source ->
                            source.toString().endsWith("/" + name.replace('.', '/') + ".java")
                                || source.toString().contains("/" + name.replace('.', '/') + "/")))
                .as(name)
                .isTrue());
  }

  @Test
  void everyClassLoggingMasterStepIsRoutedToTheMasterFeed() throws IOException {
    String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
    List<String> logging =
        SourceRoots.mainJavaFiles()
            .filter(source -> SourceRoots.read(source).contains("setMessage(\"master "))
            .map(source ->
                source
                    .toString()
                    .replaceFirst(".*main/java/", "")
                    .replace(".java", "")
                    .replace('/', '.'))
            .toList();

    assertThat(logging)
        .isNotEmpty()
        .allSatisfy(name ->
            assertThat(config)
                .as(name)
                .contains("<logger name=\"" + name + "\"><appender-ref ref=\"MASTER\"/>"));
  }

  @Test
  void theFeedsTimestampIsWrittenWithoutTerminalColourCodes() throws IOException {
    String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

    assertThat(config).contains("<pattern>%d{yyyy-MM-dd HH:mm:ssX, UTC} ");
  }

  @Test
  void everyAppenderGivesTaskLineItsAlias() throws IOException {
    String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

    var appenders =
        Pattern.compile("(?s)<appender name=\"(\\w+)\".*?</appender>")
            .matcher(config)
            .results()
            .filter(match -> match.group().contains("TaskAliasLogFilter"))
            .map(match -> match.group(1))
            .toList();

    assertThat(appenders)
        .containsExactlyInAnyOrder(
            "CONSOLE",
            "CONSOLE",
            "FILE",
            "MASTER",
            "RECENT",
            "ROLE_master",
            "ROLE_acceptance",
            "ROLE_triage");
  }
}
