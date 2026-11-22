package dev.jagt.orchestrator.service.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingLinesTest {

  @Test
  void keepsTheCommentsTheBlankLinesAndTheKeyOrderWhenValueChanges() {
    String file =
        """
        # head
        orchestrator:
          # why
          codeReview:
            mrTitlePattern: "{ticket} {title}"

            postReviewReplies: true    # false leaves every reply a draft
            reviewReplyAuthors: []
          autoReview:
            enabled: false
        """;

    String edited =
        SettingLines.changed(
            file, List.of("orchestrator", "codeReview", "postReviewReplies"), false);

    assertThat(edited)
        .isEqualTo(
            """
            # head
            orchestrator:
              # why
              codeReview:
                mrTitlePattern: "{ticket} {title}"

                postReviewReplies: false   # false leaves every reply a draft
                reviewReplyAuthors: []
              autoReview:
                enabled: false
            """);
  }

  @Test
  void keepsTheColumnOfTheCommentWhereTheNewValueIsLonger() {
    String file =
        """
        orchestrator:
          tracker:
            mode: off      # off | take
        """;

    String edited = SettingLines.changed(file, List.of("orchestrator", "tracker", "mode"), "both");

    assertThat(edited).contains("    mode: \"both\"   # off | take");
  }

  @Test
  void writesMissingKeyAtTheEndOfItsSectionBeforeTheCommentsThatFollowIt() {
    String file =
        """
        orchestrator:
          autoReview:
            enabled: false
            # windowHours: 24
          infraWait:
            backoff: [5m]
        """;

    String edited =
        SettingLines.changed(file, List.of("orchestrator", "autoReview", "windowHours"), 12);

    assertThat(edited)
        .isEqualTo(
            """
            orchestrator:
              autoReview:
                enabled: false
                windowHours: 12
                # windowHours: 24
              infraWait:
                backoff: [5m]
            """);
  }

  @Test
  void writesMissingSectionWithItsKey() {
    String file =
        """
        orchestrator:
          projects: {}
        """;

    String edited =
        SettingLines.changed(
            file, List.of("orchestrator", "codeReview", "postReviewReplies"), true);

    assertThat(edited)
        .isEqualTo(
            """
            orchestrator:
              projects: {}
              codeReview:
                postReviewReplies: true
            """);
  }

  @Test
  void rewritesInlineSectionAsBlockWhenKeyInsideItChanges() {
    String file =
        """
        orchestrator:
          codeReview: {mrTitlePattern: "{ticket}", postReviewReplies: true}   # review
        """;

    String edited =
        SettingLines.changed(
            file, List.of("orchestrator", "codeReview", "postReviewReplies"), false);

    assertThat(edited)
        .isEqualTo(
            """
            orchestrator:
              codeReview:  # review
                mrTitlePattern: "{ticket}"
                postReviewReplies: false
            """);
  }

  @Test
  void refusesToWriteValueOverSectionAndSectionUnderValue() {
    String file =
        """
        orchestrator:
          autoReview:
            enabled: false
        """;

    assertThatThrownBy(() -> SettingLines.changed(file, List.of("orchestrator", "autoReview"), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("autoReview holds a section, not a value");
    assertThatThrownBy(() ->
            SettingLines.changed(file, List.of("orchestrator", "autoReview", "enabled", "x"), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("enabled holds the value false, not a section");
  }

  @Test
  void rewritesInlineProjectEntryAsBlockKeepingItsOtherKeys() {
    String file =
        """
        orchestrator:
          projects:
            billing: {path: /old, codeHost: github, labels: [bill, pay]}   # money
            web:
              path: /srv/web
        """;
    ProjectSetup.Entry entry =
        new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null, null, null);

    String edited = ProjectBlocks.replaced(file, entry, Path.of("/p"));

    assertThat(edited)
        .isEqualTo(
            """
            orchestrator:
              projects:
                billing:  # money
                  path: "/p"
                  baseBranch: "origin/main"
                  codeHost: github
                web:
                  path: /srv/web
            """);
  }

  @Test
  void rewritesInlineProjectsMapAsBlocksWhenProjectIsAdded() {
    String file =
        """
        orchestrator:
          projects: {web: {path: /srv/web, baseBranch: main}}
        """;
    ProjectSetup.Entry entry =
        new ProjectSetup.Entry("billing", "/p", "origin/main", null, null, null, null, null);

    String edited = ProjectBlocks.inserted(file, entry, Path.of("/p"), false);

    assertThat(edited)
        .isEqualTo(
            """
            orchestrator:
              projects:
                billing:
                  path: "/p"
                  baseBranch: "origin/main"
                web:
                  path: /srv/web
                  baseBranch: main
            """);
  }
}
