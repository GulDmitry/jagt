package dev.jagt.orchestrator.job.codereview;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.service.CodeReviewSettings;
import dev.jagt.orchestrator.service.CodeReviewSettings.Settings;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;

class BotAnswerJobTest {

  private final CodeReviewSettings settings = mock(CodeReviewSettings.class);
  private final ReviewSubjects subjects = mock(ReviewSubjects.class);
  private final BotAnswers answers = mock(BotAnswers.class);
  private final ConfigService configService = mock(ConfigService.class, Answers.RETURNS_DEEP_STUBS);
  private final BotAnswerJob job = new BotAnswerJob(settings, subjects, answers, configService);

  @ParameterizedTest
  @CsvSource({"t1, true", "t1, false", "'', false"})
  void answersNothingOnOwnRequestOrWithoutPostOthers(String taskId, boolean postOthers) {
    Subject subject = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", taskId);
    when(settings.load()).thenReturn(new Settings(true, postOthers, 5000, 1));
    when(configService.load().role(CodeReviewSettings.ROLE).modelOrInherited()).thenReturn("m");
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(subject), Set.of()));

    job.run();

    verifyNoInteractions(answers);
  }

  @Test
  void answersBotOnRequestNoTaskHoldsWithPostOthers() {
    Subject subject = new Subject("shop", "https://h/a/b/7", "git@h:a/b.git", "");
    Settings read = new Settings(true, true, 5000, 1);
    when(settings.load()).thenReturn(read);
    when(configService.load().role(CodeReviewSettings.ROLE).modelOrInherited()).thenReturn("m");
    when(subjects.list()).thenReturn(new ReviewSubjects.Listing(List.of(subject), Set.of()));

    job.run();

    verify(answers).answer(subject, read, "m");
  }

  @Test
  void answersNothingWhenTheRoleIsOff() {
    when(settings.load()).thenReturn(new Settings(false, true, 5000, 1));

    job.run();

    verify(subjects, never()).list();
  }
}
