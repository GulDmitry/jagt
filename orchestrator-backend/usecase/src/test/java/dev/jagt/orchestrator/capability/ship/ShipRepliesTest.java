package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipRepliesTest {

    @Test
    void forbidsTheMasterFromAnsweringAPersonWhereThatRightWasWithheld() {
        ConfigFile config = ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act").withMine(List.of("reply")));

        String step = OriginContext.as(ActionOrigin.MASTER, () -> ShipService.repliesStep(config));

        assertThat(step).contains("may not answer a person");
    }

    @Test
    void leavesAHumansOwnShipPostingItsRepliesAsBefore() {
        ConfigFile config = ConfigFile.defaults()
                .withMaster(MasterConfig.defaults().withMode("act").withMine(List.of("reply")));

        String step = OriginContext.as(ActionOrigin.BOARD, () -> ShipService.repliesStep(config));

        assertThat(step).contains("post each drafted reply");
    }

    @Test
    void leavesTheDraftedRepliesToTheHumanWherePostingWasTurnedOff() {
        ConfigFile config = ConfigFile.defaults().withCodeReview(
                ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false));

        String step = ShipService.repliesStep(config);

        assertThat(step).isEqualTo("Do NOT post any replies — LEAVE review_replies.md untouched for the human"
                + " to post.\n");
    }

    @Test
    void postsOnlyToThreadsWhoseAuthorTheHumanNamed() {
        ConfigFile config = ConfigFile.defaults().withCodeReview(
                ConfigFile.CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("jdoe", "bot")));

        String step = ShipService.repliesStep(config);

        assertThat(step).contains("post a drafted reply ONLY where the note its block answers is by an author"
                + " matching (case-insensitive) any of: jdoe, bot");
    }
}
