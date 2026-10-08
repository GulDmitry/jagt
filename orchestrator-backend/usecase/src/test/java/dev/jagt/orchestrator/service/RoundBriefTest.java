package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ReviewFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundBriefTest {

    @Test
    void asksForRepliesInAShapeAHumanCanReadInOnePass() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("reviewer (a.java:3): drop the cache")), "success");

        assertThat(brief).contains("FIXED | NO CHANGE | QUESTION").contains("NECESSARY AND SUFFICIENT");
    }

    @Test
    void relaysAReviewRoundAsAJudgementCallAndNotAsAListOfOrders() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("reviewer (a.java:3): drop the cache")), "success");

        assertThat(brief).contains("Wrong: change NOTHING").contains("outcome=question").contains("drop the cache");
    }

    @Test
    void relaysAReviewRoundThatLeavesARightCommentBeyondTheTicketToATaskOfItsOwn() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("bot (a.java:3): also migrate the other listeners")), "success");

        assertThat(brief).contains("Right, but beyond the ticket: change NOTHING");
    }

    @Test
    void relaysAThreadWholeSoTheAgentAnswersTheReviewersLastWord() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("http://mr/1#note_7\nbot: quote the pattern\ndev: the rule IS a pattern\n"
                        + "bot: then bound the input length")), "success");

        assertThat(brief).contains("<threads>\nhttp://mr/1#note_7\nbot: quote the pattern\n"
                + "dev: the rule IS a pattern\nbot: then bound the input length");
    }

    @Test
    void tellsTheAgentToWeighTheReviewersAnswerRatherThanRepostItsOwnReply() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("http://mr/1#note_7\nbot: quote the pattern\ndev: the rule IS a pattern")), "success");

        assertThat(brief).contains("what you answer is its NEWEST note")
                .contains("never\nre-post the reply it has already read");
    }

    @Test
    void tellsTheAgentToLeaveAThreadWaitingOnTheReviewerAlone() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("http://mr/1#note_7\nbot: quote the pattern\ndev: the rule IS a pattern")), "success");

        assertThat(brief).contains("Where the newest note is your OWN and nobody\nanswered it, that thread is waiting"
                + " on the reviewer: leave it alone and give it no block.");
    }

    @Test
    void asksTheAgentToReportWhetherTheRoundChangedAnything() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "success",
                List.of("reviewer (a.java:3): drop the cache")), "success");

        assertThat(brief).contains("outcome=no_changes").contains("jagt reads the worktree")
                .contains("The file holds DRAFTS: post nothing and resolve");
    }

    @Test
    void tellsAnAgentFixingOnlyAFailedBuildWhenTheRoundIsOver() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "failed", List.of()), "failed");

        assertThat(brief).contains("When the build is fixed locally, set status REVIEW_PENDING (outcome=progress).");
    }

    @Test
    void handsTheAgentTheFailingJobsLogRatherThanOnlyTheWordFailed() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "failed",
                "test:unit\nWidgetTest > rendersLabel FAILED\n  expected 'on' but was 'off'", List.of(), 0), "failed");

        assertThat(brief).contains("<checks>\ntest:unit\nWidgetTest > rendersLabel FAILED\n"
                + "  expected 'on' but was 'off'\n</checks>");
    }

    @Test
    void briefsARedRoundToReproduceTheFailureAndAskWhereItIsNotTheCode() {
        String brief = RoundBrief.of("http://mr/1", new ReviewFacts(true, false, "failed", List.of()), "failed");

        assertThat(brief).contains("Reproduce it with the command that job ran")
                .contains("A gate fails on EVERY condition it lists: answer each.")
                .contains("A red check is this task's to turn green, code that predates it included")
                .contains("outcome=question naming the job and why");
    }
}
