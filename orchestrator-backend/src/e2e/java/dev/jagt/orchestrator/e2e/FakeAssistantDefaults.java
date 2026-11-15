package dev.jagt.orchestrator.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.assistant.Assistants;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** A read no test stubbed is the unavailable read production gives, never a mock's null failure. */
final class FakeAssistantDefaults implements BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    Assistants assistants =
        SpringExtension.getApplicationContext(context).getBean(Assistants.class);
    if (!mockingDetails(assistants.codeHost()).isMock()) {
      return;
    }
    when(assistants.codeHost().readReview(any())).thenReturn(Answer.unavailable());
    when(assistants.codeHost().readMergeRequest(any())).thenReturn(Answer.unavailable());
    when(assistants.tracker().readTicket(any(), any())).thenReturn(Answer.unavailable());
    when(assistants.tracker().readTicketText(any())).thenReturn(Answer.unavailable());
    when(assistants.tracker().findCandidates(any())).thenReturn(Answer.unavailable());
    when(assistants.routing().routeProject(any())).thenReturn(Answer.unavailable());
    when(assistants.routing().staleRule(any(), any(), any())).thenReturn(Answer.unavailable());
    when(assistants.routing().sameRules(any())).thenReturn(Answer.unavailable());
    when(assistants.reviewer().review(any())).thenReturn(Answer.unavailable());
  }
}
