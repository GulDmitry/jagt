package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.CodeReviewSettings;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.service.ConfigService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Answers what a review bot said on a request jagt may note on. */
@Service
@RequiredArgsConstructor
public class BotAnswerJob implements Job {

  private final CodeReviewSettings settings;
  private final ReviewSubjects subjects;
  private final BotAnswers answers;
  private final ConfigService configService;

  @Override
  public String id() {
    return "bot-answer";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "answer each review bot's note once, at most three times per thread";
  }

  @Override
  public Duration every() {
    return Duration.ofMinutes(1);
  }

  @Override
  public void run() {
    Settings read = settings.load();
    if (!read.enabled()) {
      return;
    }
    String model = configService.load().role(CodeReviewSettings.ROLE).modelOrInherited();
    subjects.list().subjects().stream()
        .filter(subject -> subject.own() ? read.postOwn() : read.postOthers())
        .forEach(subject -> answers.answer(subject, read, model));
  }
}
