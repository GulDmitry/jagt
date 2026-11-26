package dev.jagt.orchestrator.adapter.replay;

import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The Master's reads in recorded order. Roles read one round side by side, so within the earliest
 * round left the role a prompt names answers it, else the next read in order.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "replay")
public class RecordedVerdicts {

  /**
   * The code reviewer's reads, by the lead each opens with, and the role a recording names them by.
   */
  public static final String REVIEW_ROLE = "code-reviewer";

  public static final String ANSWER_ROLE = "bot-answer";

  private static final Map<String, String> CODE_REVIEWER_ROLES =
      Map.of(RoundReviewer.REVIEW_LEAD, REVIEW_ROLE, RoundReviewer.ANSWER_LEAD, ANSWER_ROLE);

  private final Recording recording;
  private final List<RecordingLine.Judged> left;
  private final List<RecordingLine.Answered> answers;

  public RecordedVerdicts(Recording recording) {
    this.recording = recording;
    this.left =
        new ArrayList<>(
            recording.lines().stream()
                .filter(RecordingLine.Judged.class::isInstance)
                .map(RecordingLine.Judged.class::cast)
                .toList());
    this.answers =
        new ArrayList<>(
            recording.lines().stream()
                .filter(RecordingLine.Answered.class::isInstance)
                .map(RecordingLine.Answered.class::cast)
                .toList());
  }

  public synchronized Optional<Judgement> answer(String prompt) {
    if (prompt.contains(RoundReviewer.QUESTION_LEAD)) {
      return decision();
    }
    Optional<String> reviewer =
        CODE_REVIEWER_ROLES.entrySet().stream()
            .filter(lead -> prompt.startsWith(lead.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    if (reviewer.isPresent()) {
      return codeReviewer(reviewer.get());
    }
    List<RecordingLine.Judged> master =
        left.stream().filter(read -> !CODE_REVIEWER_ROLES.containsValue(read.role())).toList();
    if (master.isEmpty()) {
      recording.diverge("", "master", "the Master read a round the recording holds no verdict for");
      return Optional.empty();
    }
    long round = master.getFirst().round();
    List<RecordingLine.Judged> current =
        master.stream().filter(read -> read.round() == round).toList();
    RecordingLine.Judged taken =
        current.stream()
            .filter(read -> prompt.contains(" " + read.role() + " "))
            .findFirst()
            .orElse(current.getFirst());
    left.remove(taken);
    return Optional.of(taken.judgement());
  }

  private Optional<Judgement> codeReviewer(String role) {
    Optional<RecordingLine.Judged> taken =
        left.stream().filter(read -> read.role().equals(role)).findFirst();
    if (taken.isEmpty()) {
      recording.diverge(
          "", role, "the code reviewer read a request the recording holds no verdict for");
      return Optional.empty();
    }
    left.remove(taken.get());
    return Optional.of(taken.get().judgement());
  }

  private Optional<Judgement> decision() {
    if (answers.isEmpty()) {
      recording.diverge(
          "", "master", "the Master answered a question the recording holds no decision for");
      return Optional.empty();
    }
    Finding decided = new Finding("", answers.removeFirst().decision(), "", "", "", "");
    return Optional.of(
        new Judgement("", Verdict.READY, List.of(decided), "", List.of(), "", Press.NONE));
  }
}
