package dev.jagt.orchestrator.adapter.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeTranscriptsTest {

  @Test
  void answersWhenTheNewestOfASessionsLogsWasLastAppendedTo(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("earlier.jsonl"), "{\"entrypoint\":\"cli\"}"),
        FileTime.fromMillis(1_700_000_000_000L));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("current.jsonl"), "{\"entrypoint\":\"cli\"}"),
        FileTime.fromMillis(1_700_000_600_000L));

    long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

    assertThat(at).isEqualTo(1_700_000_600_000L);
  }

  @Test
  void readsNoSignOfTheSessionFromAHeadlessRunInItsDirectory(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.setLastModifiedTime(
        Files.writeString(
            logs.resolve("session.jsonl"),
            """
            {"type":"mode","mode":"normal"}
            {"type":"user","entrypoint":"cli"}
            """),
        FileTime.fromMillis(1_700_000_000_000L));
    Files.setLastModifiedTime(
        Files.writeString(
            logs.resolve("headless.jsonl"),
            """
            {"type":"user","entrypoint":"sdk-cli"}
            """),
        FileTime.fromMillis(1_700_000_600_000L));

    long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

    assertThat(at).isEqualTo(1_700_000_000_000L);
  }

  @Test
  void answersNothingWhereNoLogsForThatDirectoryExist(@TempDir Path root) {
    long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

    assertThat(at).isZero();
  }

  @Test
  void findsTheLogsOfASessionDirectoryReachedThroughASymlink(@TempDir Path root) throws Exception {
    Path real = Files.createDirectories(root.resolve("real").resolve("ABC-1-proj"));
    Files.createSymbolicLink(root.resolve("link"), root.resolve("real"));

    String throughTheLink = ClaudeTranscripts.slug(root.resolve("link").resolve("ABC-1-proj"));

    assertThat(throughTheLink).isEqualTo(ClaudeTranscripts.slug(real));
  }

  @Test
  void cutsTheLineJagtTypedOutOfWhatTheHumanWasComposing(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"do itRe-read the file."}}
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
        """);

    var said =
        ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of("Re-read the file."));

    assertThat(said).contains(List.of("do it"));
  }

  @Test
  void leavesOutWhatAHeadlessRunWasPromptedWith(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"sdk-cli","message":{"content":"You stand in for the human"}}
        """);

    var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

    assertThat(said).contains(List.of());
  }

  @Test
  void answersUnreadableRatherThanSilentWhereTheSessionKeptNoRecord(@TempDir Path root) {
    var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

    assertThat(said).isEmpty();
  }

  @Test
  void readsNoSignOfTheSessionFromAFileThatIsNotALog(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("session.jsonl"), "{\"entrypoint\":\"cli\"}"),
        FileTime.fromMillis(1_700_000_000_000L));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("notes.txt"), "{\"entrypoint\":\"cli\"}"),
        FileTime.fromMillis(1_700_000_600_000L));

    long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

    assertThat(at).isEqualTo(1_700_000_000_000L);
  }

  @Test
  void leavesOutWhatWasTypedInAFileThatIsNotALog(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("notes.txt"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"do it"}}
        """);

    var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

    assertThat(said).contains(List.of());
  }

  @Test
  void namesTheNewestLogOfTheSessionAndNoOtherFile(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("session.jsonl"), "{}"),
        FileTime.fromMillis(1_700_000_000_000L));
    Files.setLastModifiedTime(
        Files.writeString(logs.resolve("notes.txt"), "{}"),
        FileTime.fromMillis(1_700_000_600_000L));

    var newest = ClaudeTranscripts.newestLog(root, Path.of("/wt/ABC-1-proj"));

    assertThat(newest).contains(logs.resolve("session.jsonl"));
  }

  @Test
  void readsWhatTheHumanTypedWhereTheCliSplitItIntoTextBlocks(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":[{"type":"text","text":"do "},{"type":"text","text":"it"}]}}
        """);

    var said = ClaudeTranscripts.humanSaid(root, Path.of("/wt/ABC-1-proj"), Set.of());

    assertThat(said).contains(List.of("do it"));
  }

  @Test
  void readsNoSignOfTheSessionFromALogItCannotRead(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.setLastModifiedTime(
        Files.createDirectories(logs.resolve("unreadable.jsonl")),
        FileTime.fromMillis(1_700_000_600_000L));

    long at = ClaudeTranscripts.lastEntryMillis(root, Path.of("/wt/ABC-1-proj"));

    assertThat(at).isZero();
  }

  @Test
  void aSuiteRunReadsSessionLogsFromInsideTheBuildNeverFromTheHumansHome() {
    assertThat(ClaudeTranscripts.projectsDir()).startsWithRaw(Path.of("").toAbsolutePath());
  }

  @Test
  void tailsTheHumansPromptsAndTheModelsTextAndNothingTheToolsSaid(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("session.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"fix the login"}}
        {"type":"assistant","message":{"content":[{"type":"text","text":"Looking at it."},{"type":"tool_use","input":{"command":"SECRET-INPUT"}}]}}
        {"type":"user","entrypoint":"cli","message":{"content":[{"type":"tool_result","content":"SECRET-OUTPUT"}]}}
        {"type":"assistant","message":{"content":[{"type":"text","text":"Fixed."}]}}
        """);

    String tail = ClaudeTranscripts.tail(root, Path.of("/wt/ABC-1-proj"), 16_000, Set.of()).orElseThrow();

    assertThat(tail)
        .isEqualTo("human: fix the login\nassistant: Looking at it.\nassistant: Fixed.");
  }

  @Test
  void keepsTheNewestWordsWhenTheTailExceedsItsCap(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("session.jsonl"),
        """
        {"type":"assistant","message":{"content":[{"type":"text","text":"oldest words"}]}}
        {"type":"assistant","message":{"content":[{"type":"text","text":"newest words"}]}}
        """);

    String tail = ClaudeTranscripts.tail(root, Path.of("/wt/ABC-1-proj"), 30, Set.of()).orElseThrow();

    assertThat(tail).isEqualTo(" words\nassistant: newest words");
  }

  @Test
  void tailsNothingWhereTheSessionKeepsNoLog(@TempDir Path root) {
    assertThat(ClaudeTranscripts.tail(root, Path.of("/wt/ABC-1-proj"), 16_000, Set.of())).isEmpty();
  }

  @Test
  void keepsNoEmptyLineWhenTheCapIsFilledExactlyByTheNewestWords(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("session.jsonl"),
        """
        {"type":"assistant","message":{"content":[{"type":"text","text":"aa"}]}}
        {"type":"assistant","message":{"content":[{"type":"text","text":"bb"}]}}
        """);

    String tail = ClaudeTranscripts.tail(root, Path.of("/wt/ABC-1-proj"), 14, Set.of()).orElseThrow();

    assertThat(tail).isEqualTo("assistant: bb");
  }

  @Test
  void tailsNoPromptTheHumanLeftBlank(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("session.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"  "}}
        {"type":"assistant","message":{"content":[{"type":"text","text":"Fixed."}]}}
        """);

    String tail = ClaudeTranscripts.tail(root, Path.of("/wt/ABC-1-proj"), 16_000, Set.of()).orElseThrow();

    assertThat(tail).isEqualTo("assistant: Fixed.");
  }

  @Test
  void tailsNoLineJagtTypedAsIfTheHumanSaidIt(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("session.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"fix the login"}}
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why red? Re-read the file."}}
        """);

    String tail =
        ClaudeTranscripts.tail(
                root, Path.of("/wt/ABC-1-proj"), 16_000, Set.of("Re-read the file."))
            .orElseThrow();

    assertThat(tail).isEqualTo("human: fix the login\nhuman: why red?");
  }

  @Test
  void readsTheNewestPromptAsTheHumansWhenItIsNotWhatJagtTyped(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        """);

    assertThat(
            ClaudeTranscripts.humanExchangeOpen(
                root, Path.of("/wt/ABC-1-proj"), Set.of("Re-read the file.")))
        .isTrue();
  }

  @Test
  void readsNoOpenExchangeWhenTheAssistantEndedItsTurnAfterTheHumansPrompt(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        {"type":"assistant","message":{"stop_reason":"end_turn","content":[{"type":"text","text":"A typo."}]}}
        """);

    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isFalse();
  }

  @Test
  void readsNoOpenExchangeWhenASystemTurnDurationFollowsTheHumansPrompt(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        {"type":"system","subtype":"turn_duration","durationMs":1200}
        """);

    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isFalse();
  }

  @Test
  void readsAnOpenExchangeWhileTheAssistantOnlyCallsToolsAfterTheHumansPrompt(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        {"type":"assistant","message":{"stop_reason":"tool_use","content":[{"type":"tool_use","name":"Bash"}]}}
        """);

    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isTrue();
  }

  @Test
  void readsAnOpenExchangeWhenAToolCallOnlyMentionsEndTurn(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        {"type":"assistant","message":{"stop_reason":"tool_use","content":[{"type":"tool_use","name":"Grep","input":{"pattern":"end_turn"}}]}}
        """);

    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isTrue();
  }

  @Test
  void readsTheNewestPromptAsJagtsWhenItIsWhatJagtTypedAfterTheHumansLast(@TempDir Path root)
      throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.writeString(
        logs.resolve("s.jsonl"),
        """
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"why is it red?"}}
        {"type":"user","entrypoint":"cli","origin":{"kind":"human"},"message":{"content":"Re-read the file."}}
        """);

    assertThat(
            ClaudeTranscripts.humanExchangeOpen(
                root, Path.of("/wt/ABC-1-proj"), Set.of("Re-read the file.")))
        .isFalse();
  }

  @Test
  void readsNoHumanPromptWhereTheSessionKeptNoRecord(@TempDir Path root) {
    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isFalse();
  }

  @Test
  void readsNoHumanPromptFromARecordItCannotRead(@TempDir Path root) throws Exception {
    Path logs = Files.createDirectories(root.resolve("-wt-ABC-1-proj"));
    Files.createDirectory(logs.resolve("s.jsonl"));

    assertThat(ClaudeTranscripts.humanExchangeOpen(root, Path.of("/wt/ABC-1-proj"), Set.of()))
        .isFalse();
  }
}
