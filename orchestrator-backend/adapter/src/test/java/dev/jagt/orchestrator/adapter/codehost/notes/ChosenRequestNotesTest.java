package dev.jagt.orchestrator.adapter.codehost.notes;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChosenRequestNotesTest {

  private static final String REQUEST = "https://git.corp.invalid/acme/shop/-/merge_requests/7";
  private static final String TWO_NOTES =
      """
      [{"id":"d1","notes":[
        {"id":31,"body":"Null here","system":false,"resolvable":true,"resolved":false,
         "author":{"username":"scanner"}},
        {"id":32,"body":"Style","system":false,"resolvable":true,"resolved":false,
         "author":{"username":"linter"}}]}]
      """;

  private final Processes processes = mock(Processes.class);
  private final ConfigService configService = mock(ConfigService.class);
  private final ChosenRequestNotes notes =
      new ChosenRequestNotes(
          configService,
          List.of(new GitLabNotes(processes, new ReachLedger())),
          new UnlistedBots(configService));

  @BeforeEach
  void hostAnswers() {
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "{\"username\":\"jagt-bot\"}", ""),
            new Processes.Result(0, TWO_NOTES, ""));
  }

  @Test
  void sharedBotReadsBotOnProjectThatListsNone() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(new CodeReviewConfig(null, null, null, null, List.of("scanner")))
                .withProjects(Map.of("shop", shopProject().withKind("gitlab").build())));

    List<RequestNote> read = notes.notes("shop", REQUEST).facts().get();

    assertThat(read)
        .extracting(RequestNote::author, RequestNote::kind)
        .contains(tuple("scanner", AuthorKind.BOT));
  }

  @Test
  void sharedAndProjectsOwnBotsBothReadBot() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(new CodeReviewConfig(null, null, null, null, List.of("scanner")))
                .withProjects(
                    Map.of(
                        "shop",
                        shopProject()
                            .withKind("gitlab")
                            .withReviewBots(List.of("linter"))
                            .build())));

    List<RequestNote> read = notes.notes("shop", REQUEST).facts().get();

    assertThat(read).extracting(RequestNote::kind).containsOnly(AuthorKind.BOT);
  }

  @Test
  void warnsNamingSharedKeyForReplyAuthorNoBotListNames() {
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(
                    CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("scanner")))
                .withProjects(Map.of("shop", shopProject().withKind("gitlab").build())));
    try (FakeLogCapture log = new FakeLogCapture(UnlistedBots.class)) {
      notes.notes("shop", REQUEST);

      assertThat(log.events())
          .singleElement()
          .satisfies(event ->
              assertThat(event.getKeyValuePairs())
                  .extracting(pair -> pair.key + "=" + pair.value)
                  .contains("author=scanner", "fix=add it to codeReview.reviewBots"));
    }
  }
}
