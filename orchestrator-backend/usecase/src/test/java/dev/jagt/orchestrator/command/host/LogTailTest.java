package dev.jagt.orchestrator.command.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LogTailTest {

  @Test
  void readsAnEntryAsTimeLevelMessageAndItsFields(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(
        log,
        "{\"@timestamp\":\"2026-11-09T19:18:10.085077Z\",\"log\":{\"level\":\"WARN\","
            + "\"logger\":\"a.B\"},\"service\":{\"name\":\"x\"},\"message\":\"job stopped\","
            + "\"job\":\"intake\",\"ecs\":{\"version\":\"8.11\"}}\n");

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).endsWith("\n11-09 19:18:10 WARN job stopped job=intake");
  }

  @Test
  void keepsLineThatIsNotAnEntryAsItIs(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(log, "Exception in thread main\n");

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).endsWith("\nException in thread main");
  }

  @Test
  void showsOnlyTheLastTwoHundredLines(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(
        log, IntStream.range(0, 201).mapToObj(i -> "line " + i).collect(Collectors.joining("\n")));

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown)
        .contains("the last 200 lines")
        .doesNotContain("line 0\n")
        .contains("line 1\n");
  }

  @Test
  void cutsLineLongerThanTheCapAndFlagsIt(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(log, "x".repeat(501));

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).endsWith("\n" + "x".repeat(500) + " [cut]");
  }

  @Test
  void keepsLineOfExactlyTheCapWhole(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(log, "x".repeat(500));

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).endsWith("\n" + "x".repeat(500));
  }

  @Test
  void dropsTheCutFirstLineWhereTheFileIsLongerThanWhatIsRead(@TempDir Path dir)
      throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(log, "cut-in-half " + "y".repeat(262_144 + 10) + "\nlast line\n");

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).contains("the last 1 lines").endsWith("\nlast line");
  }

  @Test
  void saysWhereTheLogWouldBeWhereThereIsNone(@TempDir Path dir) {
    Path log = dir.resolve("jagt.log");

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).isEqualTo("the log " + log + " holds nothing yet");
  }

  @Test
  void namesWhyTheLogCannotBeReadWhereItIsLocked(@TempDir Path dir) throws IOException {
    Path log = dir.resolve("jagt.log");
    Files.writeString(log, "a line");
    Files.setPosixFilePermissions(log, PosixFilePermissions.fromString("---------"));

    String shown = new LogTail(new JsonMapper(), log.toString()).render();

    assertThat(shown).startsWith("the log " + log + " could not be read: ");
  }
}
