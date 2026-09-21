package dev.jagt.orchestrator.protocol;

import org.junit.jupiter.api.Test;

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
}
