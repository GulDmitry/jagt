package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.task.review.AiLine;
import java.util.List;

public final class NotesOpenWithTheAiLine implements Invariant {

  public static final String NAME = "notes-open-with-the-ai-line";

  @Override
  public List<Violation> check(RunRecord run) {
    return run.reviews().notes().stream()
        .filter(RunRecord.Note::byActor)
        .filter(note -> !AiLine.opens(note.body()))
        .map(note ->
            new Violation(
                NAME,
                note.at(),
                "note "
                    + note.id()
                    + " on request "
                    + note.pull()
                    + " does not open with the AI line"))
        .toList();
  }
}
