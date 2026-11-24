package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.service.CodeReviewSettings;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.service.ConfigService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads the requests jagt's own tasks hold and those the host names for review. */
@Service
@RequiredArgsConstructor
public class CodeReviewJob implements Job {

  private final CodeReviewSettings settings;
  private final ReviewSubjects subjects;
  private final CodeReviewRound round;
  private final ConfigService configService;

  @Override
  public String id() {
    return "code-review";
  }

  @Override
  public Serves serves() {
    return Serves.TASKS;
  }

  @Override
  public String describe() {
    return "read each request's change once per new head and note what a reviewer would";
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
    subjects.list().subjects().forEach(subject -> round.run(subject, read, model));
  }
}
