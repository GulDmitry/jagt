package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewDraftsTest {

    @Test
    void announcesNothingForRepliesDraftedBeforeTheOpenRound(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, true);

        assertThat(pending).isFalse();
    }

    @Test
    void announcesRepliesDraftedForTheRoundThatIsStillOpen(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(3_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, true);

        assertThat(pending).isTrue();
    }

    @Test
    void keepsAnnouncingRepliesNoShipWillEverPost(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, false);

        assertThat(pending).isTrue();
    }

    @Test
    void announcesRepliesDraftedInTheInstantTheRoundOpened(@TempDir Path worktree) throws IOException {
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(2_000));

        boolean pending = ReviewDrafts.pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING, true);

        assertThat(pending).isTrue();
    }

    @Test
    void readsTheShipPostingEveryDraftFromTheConfigurationWhenAskedWithoutIt(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean pending = new ReviewDrafts(config).pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING);

        assertThat(pending).isFalse();
    }

    @Test
    void countsRepliesDraftedBeforeTheOpenRoundAsSpent(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean spent = new ReviewDrafts(config).spent(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build());

        assertThat(spent).isTrue();
    }

    @Test
    void neverCountsRepliesAsSpentWhereTheHumanSwitchedPostingOff(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults().withCodeReview(
                ConfigService.ConfigFile.CodeReviewConfig.defaults().withPostReviewReplies(false)));
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean spent = new ReviewDrafts(config).spent(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build());

        assertThat(spent).isFalse();
    }

    @Test
    void announcesRepliesDraftedForTheOpenRoundWhenAskedWithoutTheConfiguration(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(3_000));

        boolean pending = new ReviewDrafts(config).pending(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build(), TaskStatus.REVIEW_PENDING);

        assertThat(pending).isTrue();
    }

    @Test
    void holdsDraftsWhereTheFileCarriesRepliesForTheOpenRound(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(3_000));

        boolean drafted = new ReviewDrafts(config).drafted(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build());

        assertThat(drafted).isTrue();
    }

    @Test
    void holdsNoDraftsWhereTheFileIsBlank(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.writeString(worktree.resolve("review_replies.md"), "  \n\n");

        boolean drafted = new ReviewDrafts(config).drafted(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build());

        assertThat(drafted).isFalse();
    }

    @Test
    void holdsNoDraftsWhereTheRepliesWereShippedWithAnEarlierRound(@TempDir Path worktree) throws IOException {
        ConfigService config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
        Files.setLastModifiedTime(Files.writeString(worktree.resolve("review_replies.md"), "> rename\n\nDone.\n"),
                FileTime.fromMillis(1_000));

        boolean drafted = new ReviewDrafts(config).drafted(TaskState.builder("api", worktree.toString(),
                TaskStatus.REVIEW_PENDING).mrCreatedAt(2_000).build());

        assertThat(drafted).isFalse();
    }
}
