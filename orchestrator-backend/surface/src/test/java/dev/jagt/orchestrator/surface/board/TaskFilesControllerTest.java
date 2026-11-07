package dev.jagt.orchestrator.surface.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.service.PlanBaseline;
import dev.jagt.orchestrator.service.TaskArtifacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskFilesControllerTest {

  private final TaskArtifacts artifacts = mock(TaskArtifacts.class);
  private final PlanBaseline baseline = mock(PlanBaseline.class);
  private final TaskFilesController api = new TaskFilesController(artifacts, baseline);

  @Test
  void listsWhatTheWorktreeHoldsNow() {
    when(artifacts.present("ABC-42")).thenReturn(List.of("plan.md"));

    assertThat(api.files("ABC-42")).containsExactly("plan.md");
  }

  @Test
  void answersADocumentAsHtmlUnderItsName() {
    when(artifacts.read("ABC-42", "plan.md")).thenReturn("# Plan");
    when(baseline.since("ABC-42", "# Plan")).thenReturn(LineChange.none());

    var rendered = api.file("ABC-42", "plan.md");

    assertThat(rendered.name()).isEqualTo("plan.md");
    assertThat(rendered.html()).contains("<h1>Plan</h1>");
  }

  @Test
  void marksThePlanAgainstTheLastApprovedOneAndNoOtherDocument() {
    when(artifacts.read("ABC-42", "plan.md")).thenReturn("# Plan\n\nnew\n");
    when(baseline.since("ABC-42", "# Plan\n\nnew\n"))
        .thenReturn(new LineChange(Set.of(2), Map.of()));
    when(artifacts.read("ABC-42", "task_notes.md")).thenReturn("new\n");

    assertThat(api.file("ABC-42", "plan.md").html()).contains("<p class=\"changed\">new</p>");
    assertThat(api.file("ABC-42", "task_notes.md").html()).doesNotContain("changed");
  }
}
