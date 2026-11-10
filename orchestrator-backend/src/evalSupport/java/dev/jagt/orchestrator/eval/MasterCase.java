package dev.jagt.orchestrator.eval;

import dev.jagt.orchestrator.service.master.MasterReview;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.List;
import java.util.Map;

public record MasterCase(
    String name,
    String instructions,
    TaskStatus status,
    Map<String, String> baseline,
    Map<String, String> change,
    String plan,
    Expect expect,
    Answer answer) {

  public record Expect(MasterReview.Kind verdict, List<String> names) {}

  public record Answer(String question, List<String> said, List<String> refused) {

    static final Answer NONE = new Answer("", List.of(), List.of());
  }

  public boolean answers() {
    return answer != Answer.NONE;
  }
}
