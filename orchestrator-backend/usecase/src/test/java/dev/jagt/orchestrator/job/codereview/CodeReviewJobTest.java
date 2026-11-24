package dev.jagt.orchestrator.job.codereview;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.CodeReviewSettings;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class CodeReviewJobTest {

  private final CodeReviewSettings settings = mock(CodeReviewSettings.class);
  private final ReviewSubjects subjects = mock(ReviewSubjects.class);
  private final CodeReviewRound round = mock(CodeReviewRound.class);
  private final ConfigService configService = mock(ConfigService.class, Answers.RETURNS_DEEP_STUBS);
  private final PreShipReview preShip = mock(PreShipReview.class);
  private final CodeReviewJob job =
      new CodeReviewJob(settings, subjects, round, configService, preShip);

  @Test
  void readsTheChangeOfTasksBeforeTheirShipWhenTheRoleIsOn() {
    when(settings.load()).thenReturn(Settings.defaults());
    when(configService.load().role(CodeReviewSettings.ROLE).modelOrInherited()).thenReturn("m");
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(), Set.of()));

    job.run();

    verify(preShip).run("m");
  }

  @Test
  void readsNothingWhenTheRoleIsOff() {
    when(settings.load()).thenReturn(new Settings(false, true, false, 5000, 1));

    job.run();

    verifyNoInteractions(preShip, subjects, round);
  }
}
