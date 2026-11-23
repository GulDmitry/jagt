package dev.jagt.orchestrator.stand.invariant;

import dev.jagt.orchestrator.service.codereview.NotePoster;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NoNoteTwice implements Invariant {

  public static final String NAME = "no-note-twice";

  private static final Pattern KEY =
      Pattern.compile(Pattern.quote(NotePoster.MARKER) + "([0-9a-f]+)");

  @Override
  public List<Violation> check(RunRecord run) {
    Set<String> seen = new HashSet<>();
    return run.reviews().notes().stream()
        .filter(RunRecord.Note::byActor)
        .filter(note -> {
          Matcher key = KEY.matcher(note.body());
          return key.find() && !seen.add(note.pull() + " " + key.group(1));
        })
        .map(note ->
            new Violation(
                NAME,
                note.at(),
                "note " + note.id() + " repeats a key already posted on request " + note.pull()))
        .toList();
  }
}
