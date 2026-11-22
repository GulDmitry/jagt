package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewVerdictsTest {

  private final MasterReview reviews = mock(MasterReview.class);
  private final ReviewVerdicts verdicts = new ReviewVerdicts(reviews);
  private final TaskState task =
      TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();

  @Test
  void saysNothingWhereTheReviewLeftNoVerdict() {
    when(reviews.of(task)).thenReturn(Optional.empty());

    assertThat(verdicts.said(task)).isEmpty();
  }
}
