package dev.jagt.orchestrator.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ToolMessageTest {

  @Test
  void refusesVerbWithNoTaskToActOn() {
    assertThat(new TaskRef("").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void refusesSessionModeThisMachineDoesNotHave() {
    assertThat(new SessionStart("ABC-42", "turbo").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("mode");
  }

  @Test
  void letsTheEditorOpenTheCallersOwnWorktreeWithoutNamingIt() {
    assertThat(new IdeOpen("", "diff").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesAnEditorModeThisMachineDoesNotHave() {
    assertThat(new IdeOpen("ABC-42", "sidebyside").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("mode");
  }

  @Test
  void refusesRelayThatWouldEraseTheStandingInstructionWithNothing() {
    assertThat(new TaskInstructions("ABC-42", "  ").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("instructions");
  }

  @Test
  void refusesBannerWithNoLineInIt() {
    assertThat(new UserNotice("jagt", "").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("message");
  }

  @Test
  void asksNothingOfToolThatAsksForNothing() {
    assertThat(new NoArguments().violations(MessageContext.NONE)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "diff,true",
        "' Diff ',true",
        "DIFF,true",
        "project,false",
        "NULL,false",
        "'  ',false"
      })
  void opensSnapshotDiffOnlyWhenTheModeSaysDiff(String mode, boolean diff) {
    assertThat(new IdeOpen("ABC-42", mode).diff()).isEqualTo(diff);
  }

  @Test
  void letsTheEditorOpenAnyOfTheModesItOffersInAnyCase() {
    assertThat(new IdeOpen("ABC-42", " PROJECT ").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void readsBlankModeAsLeftOut() {
    assertThat(new IdeOpen("ABC-42", "  ").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesRelayNamingNoTask() {
    assertThat(new TaskInstructions(" ", "do it").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("taskId");
  }

  @Test
  void acceptsRelayNamingTaskAndSayingWhatToDo() {
    assertThat(new TaskInstructions("ABC-42", "do it").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void refusesBannerWhoseLineIsBlank() {
    assertThat(new UserNotice("jagt", "  ").violations(MessageContext.NONE))
        .extracting(Violation::field)
        .containsExactly("message");
  }

  @Test
  void acceptsBannerWithLineAndReadsBlankTitleAsLeftOut() {
    UserNotice notice = new UserNotice("  ", "review round addressed");

    assertThat(notice.violations(MessageContext.NONE)).isEmpty();
    assertThat(notice.title()).isNull();
  }

  @Test
  void readsBlankTaskAsNoTask() {
    assertThat(new TaskRef("  ").taskId()).isNull();
    assertThat(new TaskRef("ABC-42").violations(MessageContext.NONE)).isEmpty();
  }

  @Test
  void sendsBackEveryRuleBrokenAsOneCorrection() {
    String refusal =
        Message.refusal(
            List.of(new Violation("taskId", "required"), new Violation("mode", "one of [plan]")));

    assertThat(refusal)
        .isEqualTo(
            "This message was not accepted. Fix every line and send it again:\n"
                + "taskId: required\nmode: one of [plan]");
  }
}
