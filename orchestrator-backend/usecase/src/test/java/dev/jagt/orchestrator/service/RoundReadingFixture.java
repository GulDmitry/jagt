package dev.jagt.orchestrator.service;

import static dev.jagt.orchestrator.service.ReviewReadFixtures.api;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.polling;
import static dev.jagt.orchestrator.service.ReviewReadFixtures.web;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.task.ReviewFacts;
import java.util.Optional;

public final class RoundReadingFixture {

  private final StateService stateService;
  private final ReviewRound reviewReader;

  public RoundReadingFixture(StateService stateService, ReviewRound reviewReader) {
    this.stateService = stateService;
    this.reviewReader = reviewReader;
  }

  public void twoRepositories(
      String task, Answer<ReviewFacts> apiRead, Answer<ReviewFacts> webRead) {
    when(stateService.task(task)).thenReturn(Optional.of(polling(api(), web())));
    when(reviewReader.read(task, "api", "http://mr/api")).thenReturn(apiRead);
    when(reviewReader.read(task, "web", "http://mr/web")).thenReturn(webRead);
  }
}
