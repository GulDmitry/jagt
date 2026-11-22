package dev.jagt.orchestrator.capability.ship;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.turn.ActionOrigin;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipServiceRepliesTest {

  @Test
  void leavesHumansOwnShipPostingItsRepliesAsBefore() {
    ConfigFile config =
        ConfigFile.defaults()
            .withArbiter(ArbiterConfig.defaults().withMode("act").withMine(List.of("reply")));

    String step = OriginContext.as(ActionOrigin.BOARD, () -> ShipService.repliesStep(config));

    assertThat(step).contains("post each drafted reply");
  }

  @Test
  void leavesTheDraftedRepliesToTheHumanWherePostingWasTurnedOff() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeReview(ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false));

    String step = ShipService.repliesStep(config);

    assertThat(step)
        .isEqualTo(
            "Do NOT post any replies — LEAVE review_replies.md untouched for the human"
                + " to post.\n");
  }

  @Test
  void postsOnlyToThreadsWhoseAuthorTheHumanNamed() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeReview(
                ConfigFile.CodeReviewConfig.defaults()
                    .withReviewReplyAuthors(List.of("jdoe", "bot")));

    String step = ShipService.repliesStep(config);

    assertThat(step)
        .contains(
            "post a drafted reply ONLY where the note its block answers is by an author"
                + " matching (case-insensitive) any of: jdoe, bot");
  }
}
