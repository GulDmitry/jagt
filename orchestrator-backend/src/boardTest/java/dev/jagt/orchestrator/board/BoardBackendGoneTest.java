package dev.jagt.orchestrator.board;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static dev.jagt.orchestrator.board.TestReplyDraftBuilder.replyDraft;

import com.microsoft.playwright.Page;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.surface.board.TaskEventStream;
import java.io.IOException;
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
  void roundOpenWhenTheBackendGoesAwayTakesNoLineForItsSession(
      @Autowired TaskEventStream events, @Autowired ApplicationContext context) throws IOException {
    replyDraft(root).withKey("ABC-18").withText("## thread 1\nFIXED - Renamed it.\n").build();
    putReviewTask("ABC-18");
    Page page = openLiveBoard();
    ask(page, "replies a18");
    assertThat(page.locator("#report-line")).isVisible();

    events.onApplicationEvent(new ContextClosedEvent(context));

    assertThat(page.locator("#offline")).isVisible();
    assertThat(page.locator("#report-line")).hasAttribute("inert", "");
  }
}
