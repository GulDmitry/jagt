package dev.jagt.orchestrator.service.launch;

import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.role.HeaderChips;
import dev.jagt.orchestrator.task.launch.PendingStart;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PendingStartChips implements HeaderChips {

  private static final int WORDS_MAX = 300;

  private final PendingStarts starts;
  private final StateService state;

  @Override
  public List<HeaderChip> chips() {
    List<PendingStart> open =
        starts.interrupted().stream()
            .filter(start -> !hasTask(start.key()))
            .sorted(Comparator.comparingLong(PendingStart::startedAt))
            .toList();
    if (open.isEmpty()) {
      return List.of();
    }
    String detail =
        String.join(
            "\n", open.stream().map(start -> start.key() + ": " + instructions(start)).toList());
    return List.of(
        new HeaderChip("start-interrupted", null, Kind.BAD, open.getFirst().startedAt(), detail));
  }

  private boolean hasTask(String key) {
    return state.tasks().keySet().stream().anyMatch(key::equalsIgnoreCase);
  }

  private static String instructions(PendingStart start) {
    String ref = start.request().ref() == null ? "" : start.request().ref() + " ";
    String notes = start.request().notes() == null ? "" : start.request().notes();
    String words = (ref + notes).strip();
    return words.length() <= WORDS_MAX ? words : words.substring(0, WORDS_MAX) + "…";
  }
}
