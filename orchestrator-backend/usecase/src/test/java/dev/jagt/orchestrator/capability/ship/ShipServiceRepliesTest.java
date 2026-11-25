package dev.jagt.orchestrator.capability.ship;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.task.review.AiLine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShipServiceRepliesTest {

  @Test
  void leavesHumansOwnShipPostingItsRepliesAsBefore() {
    ConfigFile config =
        ConfigFile.defaults()
            .withArbiter(ArbiterConfig.defaults().withMode("act").withMine(List.of("reply")));

    String step = ShipService.repliesStep(config, true);

    assertThat(step).contains("post each drafted reply");
  }

  @Test
  void leavesTheDraftedRepliesToTheHumanWherePostingWasTurnedOff() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeReview(ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false));

    String step = ShipService.repliesStep(config, true);

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

    String step = ShipService.repliesStep(config, true);

    assertThat(step)
        .contains(
            "post a drafted reply ONLY where the note its block answers is by an author"
                + " matching (case-insensitive) any of: jdoe, bot");
  }

  @Test
  void opensEachPostedReplyWithTheAiLineWhereTheMasterMayPostToPeople() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeReview(new ConfigFile.CodeReviewConfig(null, null, null, true));

    String step = ShipService.repliesStep(config, true);

    assertThat(step).contains(AiLine.TEXT);
  }

  @Test
  void asksForNoAiLineWhereTheMasterMayNotPostToPeople() {
    String step = ShipService.repliesStep(ConfigFile.defaults(), true);

    assertThat(step).doesNotContain(AiLine.TEXT);
  }

  @ParameterizedTest
  @CsvSource({
    "true, post a `bot` or `person` block",
    "false, NEVER post a `person` block, post a `bot` block"
  })
  void namesWhichKindsArePostedAsTheCellSays(boolean personReplies, String expected) {
    String step = ShipService.repliesStep(ConfigFile.defaults(), personReplies);

    assertThat(step).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void neverPostsTheCodeReviewersBlocksOrThoseOfAnUnknownKind(boolean personReplies) {
    String step = ShipService.repliesStep(ConfigFile.defaults(), personReplies);

    assertThat(step).contains("NEVER post a `reviewer` or `unknown` block");
  }

  @Test
  void namesNoKindWhereNoReplyIsPosted() {
    ConfigFile config =
        ConfigFile.defaults()
            .withCodeReview(ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false));

    String step = ShipService.repliesStep(config, true);

    assertThat(step).doesNotContain("`Author:`");
  }
}
