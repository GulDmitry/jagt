package dev.jagt.orchestrator.stand.invariant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.With;

@With
public record TestRunRecordBuilder(List<RunRecord.Note> notes, List<RunRecord.Approval> approvals) {

  public static TestRunRecordBuilder runRecord() {
    return new TestRunRecordBuilder(List.of(), List.of());
  }

  public RunRecord build() {
    return new RunRecord(
        Map.of(),
        List.of(),
        new RunRecord.Timeline(List.of(), List.of(), Set.of()),
        Map.of(),
        new RunRecord.Reviews(notes, approvals));
  }
}
