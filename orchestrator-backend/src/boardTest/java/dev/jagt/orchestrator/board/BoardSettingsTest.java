package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardSettingsTest extends BoardPageContext {

  @Autowired
  BoardSettingsTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void theGearOpensTheSettingsPageWithItsFiveSectionsAndNoRoleOrModelSetting() {
    Page page = openLiveBoard();

    page.locator("#gear").click();
    page.locator("#gear-settings").click();

    assertThat(page.locator("#settings-dialog")).isVisible();
    assertThat(page.locator("#settings-body section")).hasCount(5);
    assertThat(page.locator("#settings-body [role=tab]")).hasCount(5);
    assertThat(page.locator("#settings-body section[data-section=projects]")).isVisible();
    assertThat(page.locator("#settings-body section[data-section=review]")).isHidden();
    assertThat(page.locator("#settings-body [data-key*=model]")).hasCount(0);
    assertThat(page.locator("#settings-body [data-key^=agent]")).hasCount(0);
    assertThat(page.locator("#settings-body [data-key^='roles.']")).hasCount(1);
  }

  @Test
  void projectEditButtonNamesItsProject() {
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();

    assertThat(page.locator("#settings-body [data-project=alpha] button"))
        .hasAttribute("aria-label", "Edit alpha");
  }

  @Test
  void masterModeShowsItsDefaultCheckedAndMarkedWhileJagtYmlLeavesItOut() {
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();

    page.locator("#settings-tab-master").click();

    assertThat(page.locator("#settings-body [data-key='roles.master.mode'] input[type=radio]"))
        .hasCount(3);
    assertThat(page.locator("#settings-body [data-key='roles.master.mode'] input[value=act]"))
        .isChecked();
    assertThat(page.locator("#settings-body [data-key='roles.master.mode'] .hint"))
        .hasText("default");
  }

  @Test
  void projectAddedOnThePageLandsInJagtYmlAndIsListedThere() throws Exception {
    Path folder = Files.createDirectories(root.resolve("gamma"));
    new ProcessBuilder("git", "init", "-q", "-b", "main", folder.toString())
        .inheritIO()
        .start()
        .waitFor();
    new ProcessBuilder(
            "git",
            "-C",
            folder.toString(),
            "-c",
            "user.name=t",
            "-c",
            "user.email=t@example.com",
            "commit",
            "-q",
            "--allow-empty",
            "-m",
            "x")
        .inheritIO()
        .start()
        .waitFor();
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();

    page.locator("#settings-add-project").click();
    page.locator("#project-path").fill(folder.toString());
    assertThat(page.locator("#project-key")).hasValue("gamma");
    page.locator("#project-form button[type=submit]").click();

    assertThat(page.locator("#settings-body [data-project=gamma]")).isVisible();
    org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("jagt.yml")))
        .contains("    gamma:\n");
  }

  @Test
  void changedKeyIsRewrittenWithItsInlineSectionAsBlock() throws Exception {
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();

    page.locator("#settings-tab-review").click();
    page.locator("#settings-body [data-key='autoReview.enabled'] input").uncheck();

    assertThat(page.locator("#toasts .toast")).containsText("Changed autoReview.enabled");
    org.assertj.core.api.Assertions.assertThat(Files.readString(root.resolve("jagt.yml")))
        .contains("  autoReview:\n    enabled: false\n");
  }

  @Test
  void keyReadAtStartSaysSoAndOffersTheRestartVerb() {
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();
    assertThat(page.locator("#settings-restart")).isHidden();

    page.locator("#settings-tab-review").click();
    page.locator("#settings-body [data-key='infraWait.backoff'] input").fill("1m, 2m");
    page.locator("#settings-body [data-key='infraWait.backoff'] input").blur();

    assertThat(page.locator("#settings-restart")).isVisible();
    assertThat(page.locator("#settings-restart-text")).containsText("Pipeline re-run waits");
    com.microsoft.playwright.Request sent =
        page.waitForRequest(
            request -> request.url().endsWith("/api/commands/restart"),
            () -> page.locator("#settings-restart-go").click());
    org.assertj.core.api.Assertions.assertThat(sent.method()).isEqualTo("POST");
  }

  @Test
  void fileThatCannotBeWrittenIsSaidOnTheBoardAndStaysAsItWas() throws Exception {
    Path file = root.resolve("jagt.yml");
    Page page = openLiveBoard();
    page.locator("#gear").click();
    page.locator("#gear-settings").click();
    String before = Files.readString(file);
    file.toFile().setWritable(false);
    try {
      page.locator("#settings-tab-review").click();
      page.locator("#settings-body [data-key='autoReview.enabled'] input").uncheck();

      assertThat(page.locator("#toasts .toast.error")).containsText("not writable");
      org.assertj.core.api.Assertions.assertThat(Files.readString(file)).isEqualTo(before);
    } finally {
      file.toFile().setWritable(true);
    }
  }
}
