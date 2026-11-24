package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.CodeHostChoice;
import dev.jagt.orchestrator.adapter.codehost.HostSettings;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.NoteDraft;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.port.codehost.review.RequestNotes;
import dev.jagt.orchestrator.port.codehost.review.ReviewRequest;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asks the notes driver of the code host the project's {@code jagt.yml} chose, read on every call.
 */
@Component
@RequiredArgsConstructor
public class ChosenRequestNotes implements RequestNotes {

  private final ConfigService configService;
  private final List<NotesDriver> drivers;

  @Override
  public Answer<List<ReviewRequest>> awaitingReview(String project, String remoteUrl) {
    return ask(project, (driver, host) -> driver.awaitingReview(host, remoteUrl));
  }

  @Override
  public Answer<RequestChange> change(String project, String requestUrl) {
    return ask(project, (driver, host) -> driver.change(host, requestUrl));
  }

  @Override
  public Answer<List<RequestNote>> notes(String project, String requestUrl) {
    return ask(project, (driver, host) -> driver.notes(host, requestUrl));
  }

  @Override
  public Answer<String> post(String project, String requestUrl, NoteDraft draft) {
    return ask(project, (driver, host) -> driver.post(host, requestUrl, draft));
  }

  @Override
  public Answer<Map<String, Reaction>> reactions(
      String project, String requestUrl, List<String> noteIds) {
    return ask(project, (driver, host) -> driver.reactions(host, requestUrl, noteIds));
  }

  private <T> Answer<T> ask(
      String project, BiFunction<NotesDriver, HostSettings, Answer<T>> question) {
    return CodeHostChoice.ask(configService, project, drivers, NotesDriver::kinds, question);
  }
}
