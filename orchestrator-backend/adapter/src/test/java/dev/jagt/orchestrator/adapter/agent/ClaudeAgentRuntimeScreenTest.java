package dev.jagt.orchestrator.adapter.agent;

import static dev.jagt.orchestrator.adapter.agent.TestClaudeRuntimeBuilder.claudeRuntime;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.testing.FakeClock;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ClaudeAgentRuntimeScreenTest {

  @Test
  void refusesTurnEndInTheShapeClaudeReadsAsKeepGoing() {
    String refusal = claudeRuntime().build(new FakeClock()).refusedTurnEnd("report \"first\"");

    var answer = new JsonMapper().readTree(refusal);
    assertThat(answer.path("decision").asString("")).isEqualTo("block");
    assertThat(answer.path("reason").asString("")).isEqualTo("report \"first\"");
  }

  @Test
  void showsTheHumanLineInTheFieldClaudeKeepsFromTheModel() {
    String line = claudeRuntime().build(new FakeClock()).toldTheHuman("with the \"Master\"");

    var answer = new JsonMapper().readTree(line);
    assertThat(answer.path("systemMessage").asString("")).isEqualTo("with the \"Master\"");
  }

  @Test
  void typesShellLineBehindBang() {
    assertThat(claudeRuntime().build(new FakeClock()).shellEscape()).contains("!");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "❯\u00a0where is the merge re|true",
        "❯\u00a0\033[2mclose the task\033[0m|false",
        "❯ an earlier message\\n❯\u00a0|false"
      })
  void tellsDraftTheHumanIsComposingFromTheSuggestionAndFromWhatTheyAlreadySent(
      String screen, boolean draft) {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.holdsDraft(screen.translateEscapes())).isEqualTo(draft);
  }

  @Test
  void readsThePromptSuggestionOfAnIdleSessionAsNoDraftAndTypedLineOverItAsOne() throws Exception {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        new String(
            getClass().getResourceAsStream("/screens/suggestion-2.1.308.txt").readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);

    assertThat(runtime.holdsDraft(screen)).isFalse();
    assertThat(
            runtime.holdsDraft(
                screen.replace("\u001b[2mretry the status report\u001b[0m", "retry the sta")))
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "idle-2.1.311.txt,false,false",
    "working-2.1.311.txt,true,false",
    "queued-2.1.311.txt,true,false",
    "draft-2.1.311.txt,false,true"
  })
  void readsTheScreensOfTheInstalledCliAsWorkingAndAsHoldingDraft(
      String fixture, boolean working, boolean draft) throws Exception {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        new String(
            getClass().getResourceAsStream("/screens/" + fixture).readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);

    assertThat(runtime.working(screen)).isEqualTo(working);
    assertThat(runtime.holdsDraft(screen)).isEqualTo(draft);
  }

  @Test
  void readsTheFirstRowOfWrappedInputAsTheDraft() {
    var runtime = claudeRuntime().build(new FakeClock());

    var draft =
        runtime.draft(
            "────\n❯\u00a0task_context.md changed — re-read it now and follow the \n"
                + "  new instructions.\n────\n  ? for shortcuts");

    assertThat(draft).contains("task_context.md changed — re-read it now and follow the");
  }

  @Test
  void tellsSessionWaitingOnTheTrustDialogFromItsScreen() {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        """
        \u001b[38;5;174m╭──────────────────────────────────────────────────────────────╮\u001b[39m
        \u001b[38;5;174m│\u001b[39m \u001b[1mDo you trust the files in this folder?\u001b[22m                     \u001b[38;5;174m│\u001b[39m
        \u001b[38;5;174m│\u001b[39m /wt/ABC-42-proj                                              \u001b[38;5;174m│\u001b[39m
        \u001b[38;5;174m│\u001b[39m \u001b[36m❯ 1. Yes, \u001b[1mI trust this folder\u001b[22m\u001b[39m                            \u001b[38;5;174m│\u001b[39m
        \u001b[38;5;174m│\u001b[39m   2. No, exit                                                \u001b[38;5;174m│\u001b[39m
        \u001b[38;5;174m╰──────────────────────────────────────────────────────────────╯\u001b[39m
           Enter to confirm · Esc to exit
        """;

    assertThat(runtime.unansweredPrompt(screen)).contains("waiting on the trust dialog");
  }

  @Test
  void tellsSessionWaitingOnTheFirstRunSetupFromItsScreen() {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        """
        Let's get started.
        Choose the text style that looks best with your terminal
        ❯ 1. Dark mode
          2. Light mode
        """;

    assertThat(runtime.unansweredPrompt(screen)).contains("waiting on the first-run setup");
  }

  @Test
  void tellsSessionWaitingOnPermissionDialogFromItsScreen() {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        """
        Bash command
          git status
        Do you want to proceed?
        ❯ 1. Yes
          2. No, and tell Claude what to do differently (esc)
         Esc to cancel
        """;

    assertThat(runtime.unansweredPrompt(screen)).contains("waiting on a permission dialog");
  }

  @Test
  void readsTheNudgeAndAnIdleSessionFromTheScreenOfSessionFinishedWithItsTurn() throws Exception {
    var runtime = claudeRuntime().build(new FakeClock());
    String screen =
        new String(
            getClass().getResourceAsStream("/screens/idle-nudge.txt").readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);

    assertThat(runtime.draft(screen))
        .contains("task_context.md changed — re-read it now and follow the new instructions.");
    assertThat(runtime.working(screen)).isFalse();
  }

  @Test
  void readsTurnRunningFromItsInterruptHint() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(
            runtime.working(
                "\u001b[38;5;174m✶ Thinking…\u001b[0m (12s · esc to interrupt)\n" + "❯ "))
        .isTrue();
    assertThat(runtime.working("✶ Clauded… (3s · thinking)\n❯ ")).isTrue();
  }

  @Test
  void readsTurnRunningFromItsSpinnerLine() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(
            runtime.working(
                "\n"
                    + "\u001b[38;5;174m✶\u001b[39m"
                    + " \u001b[38;5;174mSprouti\u001b[38;5;216mng…\u001b[38;5;174m"
                    + " \u001b[39m\n"
                    + "❯ "))
        .isTrue();
    assertThat(
            runtime.working(
                "\u001b[38;5;174m✳\u001b[39m \u001b[38;5;174mSymbioting…"
                    + " \u001b[38;5;246m(5s ·"
                    + " \u001b[38;5;249mthinking\u001b[38;5;246m)\u001b[39m\n"
                    + "❯ "))
        .isTrue();
  }

  @Test
  void readsTurnIdleWhenOnlyItsFinishedLineOrDraftRemains() {
    var runtime = claudeRuntime().build(new FakeClock());

    assertThat(runtime.working("✻ Brewed for 12s\n❯ ")).isFalse();
    assertThat(runtime.working("⏺ Checking…\n❯ ")).isFalse();
    assertThat(runtime.working("❯ Sprouting…")).isFalse();
    assertThat(runtime.working("❯ re-read the file and continue…")).isFalse();
    assertThat(runtime.working("Sprouting… is a word\n❯ ")).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"3660000, 3600000, true", "3540000, 3600000, false", "999999999, 0, false"})
  void judgesConversationColdOnlyPastNonZeroCacheLifetime(
      long idleMillis, long lifetimeMillis, boolean cold) {
    var runtime =
        claudeRuntime()
            .build(
                Clock.fixed(
                    java.time.Instant.ofEpochMilli(1_000_000_000L), java.time.ZoneOffset.UTC));

    boolean past =
        runtime.pastCacheLifetime(
            1_000_000_000L - idleMillis, java.time.Duration.ofMillis(lifetimeMillis));

    assertThat(past).isEqualTo(cold);
  }

  @Test
  void blocksPromptInTheShapeClaudeReadsAsBlocked() {
    String blocked =
        claudeRuntime().build(new FakeClock()).blockedPrompt("went to a fresh session");

    assertThat(blocked)
        .isEqualTo("{\"decision\": \"block\", \"reason\": \"went to a fresh session\"}");
  }
}
