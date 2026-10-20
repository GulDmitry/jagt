package dev.jagt.orchestrator.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToolMessageTest {

    @Test
    void refusesAVerbWithNoTaskToActOn() {
        assertThat(new TaskRef(null).violations(MessageContext.NONE)).extracting(Violation::field)
                .containsExactly("taskId");
    }

    @Test
    void refusesASessionModeThisMachineDoesNotHave() {
        assertThat(new SessionStart("ABC-42", "turbo").violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("mode");
    }

    @Test
    void letsTheEditorOpenTheCallersOwnWorktreeWithoutNamingIt() {
        assertThat(new IdeOpen(null, "diff").violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void refusesAnEditorModeThisMachineDoesNotHave() {
        assertThat(new IdeOpen("ABC-42", "sidebyside").violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("mode");
    }

    @Test
    void refusesARelayThatWouldEraseTheStandingInstructionWithNothing() {
        assertThat(new TaskInstructions("ABC-42", "  ").violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("instructions");
    }

    @Test
    void refusesABannerWithNoLineInIt() {
        assertThat(new UserNotice("jagt", null).violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("message");
    }

    @Test
    void asksNothingOfAToolThatAsksForNothing() {
        assertThat(new NoArguments().violations(MessageContext.NONE)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"diff,true", "' Diff ',true", "DIFF,true", "project,false", "NULL,false",
            "'  ',false"})
    void opensASnapshotDiffOnlyWhenTheModeSaysDiff(String mode, boolean diff) {
        assertThat(new IdeOpen("ABC-42", mode).diff()).isEqualTo(diff);
    }

    @Test
    void letsTheEditorOpenAnyOfTheModesItOffersInAnyCase() {
        assertThat(new IdeOpen("ABC-42", " PROJECT ").violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void readsABlankModeAsLeftOut() {
        assertThat(new IdeOpen("ABC-42", "  ").violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void refusesARelayNamingNoTask() {
        assertThat(new TaskInstructions(" ", "do it").violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("taskId");
    }

    @Test
    void acceptsARelayNamingATaskAndSayingWhatToDo() {
        assertThat(new TaskInstructions("ABC-42", "do it").violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void refusesABannerWhoseLineIsBlank() {
        assertThat(new UserNotice("jagt", "  ").violations(MessageContext.NONE))
                .extracting(Violation::field).containsExactly("message");
    }

    @Test
    void acceptsABannerWithALineAndReadsABlankTitleAsLeftOut() {
        UserNotice notice = new UserNotice("  ", "review round addressed");

        assertThat(notice.violations(MessageContext.NONE)).isEmpty();
        assertThat(notice.title()).isNull();
    }

    @Test
    void readsABlankTaskAsNoTask() {
        assertThat(new TaskRef("  ").taskId()).isNull();
        assertThat(new TaskRef("ABC-42").violations(MessageContext.NONE)).isEmpty();
    }

    @Test
    void sendsBackEveryRuleBrokenAsOneCorrection() {
        String refusal = Message.refusal(List.of(new Violation("taskId", "required"),
                new Violation("mode", "one of [plan]")));

        assertThat(refusal).isEqualTo("This message was not accepted. Fix every line and send it again:\n"
                + "taskId: required\nmode: one of [plan]");
    }
}
