package dev.jagt.orchestrator.service.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigFile.CodeReviewConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReviewDraftsTest {

  @Test
  void announcesNothingForRepliesDraftedBeforeTheOpenRound(@TempDir Path worktree)
      throws IOException {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean pending =
        ReviewDrafts.pending(
            TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .mrCreatedAt(2_000)
                .build(),
            TaskStatus.REVIEW_PENDING,
            CodeReviewConfig.defaults());

    assertThat(pending).isFalse();
  }

  @Test
  void announcesRepliesDraftedForTheRoundThatIsStillOpen(@TempDir Path worktree)
      throws IOException {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(3_000));

    boolean pending =
        ReviewDrafts.pending(
            TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .mrCreatedAt(2_000)
                .build(),
            TaskStatus.REVIEW_PENDING,
            CodeReviewConfig.defaults());

    assertThat(pending).isTrue();
  }

  @Test
  void keepsAnnouncingRepliesNoShipWillEverPost(@TempDir Path worktree) throws IOException {
    CodeReviewConfig authorFiltered =
        CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("ABC"));
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean pending =
        ReviewDrafts.pending(
            TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .mrCreatedAt(2_000)
                .build(),
            TaskStatus.REVIEW_PENDING,
            authorFiltered);

    assertThat(pending).isTrue();
  }

  @Test
  void keepsAnnouncingRepliesNoShipWillPostAfterTheTaskLeftHandBack(@TempDir Path worktree)
      throws IOException {
    CodeReviewConfig authorFiltered =
        CodeReviewConfig.defaults().withReviewReplyAuthors(List.of("ABC"));
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(3_000));

    boolean pending =
        ReviewDrafts.pending(
            TaskState.builder("api", worktree.toString(), TaskStatus.CI_POLLING)
                .mrCreatedAt(2_000)
                .build(),
            TaskStatus.CI_POLLING,
            authorFiltered);

    assertThat(pending).isTrue();
  }

  @Test
  void announcesRepliesDraftedInTheInstantTheRoundOpened(@TempDir Path worktree)
      throws IOException {
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(2_000));

    boolean pending =
        ReviewDrafts.pending(
            TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                .mrCreatedAt(2_000)
                .build(),
            TaskStatus.REVIEW_PENDING,
            CodeReviewConfig.defaults());

    assertThat(pending).isTrue();
  }

  @Test
  void readsTheShipPostingEveryDraftFromTheConfigurationWhenAskedWithoutIt(@TempDir Path worktree)
      throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean pending =
        new ReviewDrafts(config)
            .pending(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build(),
                TaskStatus.REVIEW_PENDING);

    assertThat(pending).isFalse();
  }

  @Test
  void countsRepliesDraftedBeforeTheOpenRoundAsSpent(@TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean spent =
        new ReviewDrafts(config)
            .spent(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build());

    assertThat(spent).isTrue();
  }

  @Test
  void neverCountsRepliesAsSpentWhereTheHumanSwitchedPostingOff(@TempDir Path worktree)
      throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(
                    ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false)));
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean spent =
        new ReviewDrafts(config)
            .spent(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build());

    assertThat(spent).isFalse();
  }

  @Test
  void announcesRepliesDraftedForTheOpenRoundWhenAskedWithoutTheConfiguration(
      @TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(3_000));

    boolean pending =
        new ReviewDrafts(config)
            .pending(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build(),
                TaskStatus.REVIEW_PENDING);

    assertThat(pending).isTrue();
  }

  @Test
  void holdsDraftsWhereTheFileCarriesRepliesForTheOpenRound(@TempDir Path worktree)
      throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(3_000));

    boolean drafted =
        new ReviewDrafts(config)
            .drafted(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build());

    assertThat(drafted).isTrue();
  }

  @Test
  void holdsNoDraftsWhereTheFileIsBlank(@TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.writeString(worktree.resolve("review_replies.md"), "  \n\n");

    boolean drafted =
        new ReviewDrafts(config)
            .drafted(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build());

    assertThat(drafted).isFalse();
  }

  @Test
  void holdsNoDraftsWhereTheRepliesWereShippedWithAnEarlierRound(@TempDir Path worktree)
      throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    boolean drafted =
        new ReviewDrafts(config)
            .drafted(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                    .mrCreatedAt(2_000)
                    .build());

    assertThat(drafted).isFalse();
  }

  @Test
  void digestsDifferWhereTheUnspentRepliesDiffer(@TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    TaskState task =
        TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
            .mrCreatedAt(2_000)
            .build();
    Path file = worktree.resolve("review_replies.md");
    Files.setLastModifiedTime(
        Files.writeString(file, "> rename\n\nDone.\n"), FileTime.fromMillis(3_000));
    String first = new ReviewDrafts(config).unspentDigest(task).orElseThrow();
    Files.writeString(file, "> rename\n\nDone.\n\n> retry\n\nKept.\n");

    String second = new ReviewDrafts(config).unspentDigest(task).orElseThrow();

    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void digestsNothingWhereNoRepliesAreUnspent(@TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Files.setLastModifiedTime(
        Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
        FileTime.fromMillis(1_000));

    assertThat(
            new ReviewDrafts(config)
                .unspentDigest(
                    TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                        .mrCreatedAt(2_000)
                        .build()))
        .isEmpty();
  }

  @Test
  void digestsRepliesEvenWhereTheFileCannotBeRead(@TempDir Path worktree) throws IOException {
    ConfigService config = mock(ConfigService.class);
    when(config.load()).thenReturn(ConfigFile.defaults());
    Path file = Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n");
    Files.setPosixFilePermissions(file, java.util.Set.of());

    assertThat(
            new ReviewDrafts(config)
                .unspentDigest(
                    TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING)
                        .mrCreatedAt(0)
                        .build()))
        .isPresent();
  }

  @Test
  void holdsDraftsForTheHumanWhereTheMasterMayNotPostToPeople(@TempDir Path worktree)
      throws IOException {
    Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n");
    ConfigService configService = mock(ConfigService.class);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    boolean held =
        new ReviewDrafts(configService)
            .heldForHuman(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING).build());

    assertThat(held).isTrue();
  }

  @Test
  void holdsNoDraftsWhereTheMasterMayPostToPeopleAndEveryDraftIsPosted(@TempDir Path worktree)
      throws IOException {
    Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n");
    ConfigService configService = mock(ConfigService.class);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(new ConfigFile.CodeReviewConfig(null, null, null, true)));

    boolean held =
        new ReviewDrafts(configService)
            .heldForHuman(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING).build());

    assertThat(held).isFalse();
  }

  static Stream<Arguments> cells() {
    String person = "## http://mr/1#note_1\nAuthor: person alice\n> rename\nFIXED - Done.\n";
    String bot = "## http://mr/1#note_2\nAuthor: bot review-bot\n> typo\nFIXED - Done.\n";
    String reviewer = "## http://mr/1#note_3\nAuthor: reviewer\n> leak\nFIXED - Done.\n";
    return Stream.of(
        Arguments.of("P1 replies off", false, List.of(), true, person, true),
        Arguments.of("P2 person, Master unasked", true, List.of(), false, person, true),
        Arguments.of(
            "P2 person, filter matches, Master unasked",
            true,
            List.of("alice"),
            false,
            person,
            true),
        Arguments.of("P3 person, Master may reply", true, List.of(), true, person, false),
        Arguments.of("P3 person, filter matches", true, List.of("alice"), true, person, false),
        Arguments.of("P3 person, filter names another", true, List.of("bob"), true, person, true),
        Arguments.of("B1 bot only, Master unasked", true, List.of(), false, bot, false),
        Arguments.of("B1 bot only, filter matches", true, List.of("review-bot"), false, bot, false),
        Arguments.of(
            "B1 bot and person, Master unasked", true, List.of(), false, bot + person, true),
        Arguments.of(
            "B1 bot and person, filter names the bot",
            true,
            List.of("review-bot"),
            true,
            bot + person,
            true),
        Arguments.of("C1 code reviewer only", true, List.of(), false, reviewer, false),
        Arguments.of(
            "C1 code reviewer beside a person", true, List.of(), true, reviewer + person, false),
        Arguments.of(
            "kind unknown stays for the human whatever the Master may do",
            true,
            List.of(),
            true,
            "## http://mr/1#note_6\nAuthor: unknown dana\n> x\nFIXED - Done.\n",
            true),
        Arguments.of(
            "unknown kind reads as a person",
            true,
            List.of(),
            false,
            "## http://mr/1#note_4\nAuthor: robot\n> x\nFIXED - Done.\n",
            true),
        Arguments.of(
            "no author line reads as a person",
            true,
            List.of(),
            false,
            "## http://mr/1#note_5\n> x\nFIXED - Done.\n",
            true));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cells")
  void holdsDraftsForTheHumanAsTheMatrixCellSays(
      String cell,
      boolean postReplies,
      List<String> authors,
      boolean repliesToPeople,
      String drafts,
      boolean expected,
      @TempDir Path worktree)
      throws IOException {
    Files.writeString(worktree.resolve("review_replies.md"), drafts);
    ConfigService configService = mock(ConfigService.class);
    when(configService.load())
        .thenReturn(
            ConfigFile.defaults()
                .withCodeReview(
                    new ConfigFile.CodeReviewConfig(null, postReplies, authors, repliesToPeople)));

    boolean held =
        new ReviewDrafts(configService)
            .heldForHuman(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING).build());

    assertThat(held).as(cell).isEqualTo(expected);
  }

  @Test
  void holdsNoDraftsWhereTheFileCarriesOnlyTheCodeReviewersBlocks(@TempDir Path worktree)
      throws IOException {
    Files.writeString(
        worktree.resolve("review_replies.md"),
        "## http://mr/1#note_3\nAuthor: reviewer\n> leak\nFIXED - Done.\n");
    ConfigService configService = mock(ConfigService.class);
    when(configService.load()).thenReturn(ConfigFile.defaults());

    boolean drafted =
        new ReviewDrafts(configService)
            .drafted(
                TaskState.builder("api", worktree.toString(), TaskStatus.REVIEW_PENDING).build());

    assertThat(drafted).isFalse();
  }
}
