package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static dev.jagt.orchestrator.board.TestReplyDraftBuilder.replyDraft;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.board.TaskEventStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

// Closing the event stream is final for its context, so this one case gets a context of its own.
@TestPropertySource(properties = "orchestrator.board.backend-gone=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BoardBackendGoneTest extends BoardPageContext {

  @Autowired
  BoardBackendGoneTest(BreakableJob breakable, StateService state) {
    super(breakable, state);
  }

  @Test
  void everyControlWritingToTheBackendGoesInertAndTheLocalOnesStayLive(
      @Autowired TaskEventStream events, @Autowired ApplicationContext context) throws IOException {
    replyDraft(root).withKey("ABC-18").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-18");
    Page page = openLiveBoard();
    ask(page, "replies a18");
    assertThat(page.locator("#report-line")).isVisible();

    events.onApplicationEvent(new ContextClosedEvent(context));

    assertThat(page.locator("#offline")).isVisible();
    assertEquals(
        List.of(),
        page.evaluate(
            """
            [...document.querySelectorAll('button, input, select, textarea, form')]
              .filter(e => !e.closest('[inert]'))
              .filter(e => !e.matches('#filter, #gear, button[id^="close-"]'))
              .filter(e => !e.closest('#gear-appearance, #gear-language'))
              .filter(e => !(e.matches('form') && e.querySelector('button[id^="close-"]')))
              .map(e => e.id || e.outerHTML)
            """));
    assertEquals(
        List.of(false, false, false),
        page.evaluate(
            """
            ['#filter', '#gear-appearance', '#gear-language']
              .map(selector => document.querySelector(selector).closest('[inert]') !== null)
            """));
  }
}
