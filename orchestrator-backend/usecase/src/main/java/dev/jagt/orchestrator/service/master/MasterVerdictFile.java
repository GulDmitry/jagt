package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.master.MasterPanel.Role;
import dev.jagt.orchestrator.service.master.MasterReview.Review;
import dev.jagt.orchestrator.service.master.MasterReview.Verdict;
import java.util.List;
import java.util.stream.IntStream;

/** The file a human reads for a round: what its verdict rests on. */
final class MasterVerdictFile {

  private MasterVerdictFile() {}

  /** A role left unread and the one sentence saying why. */
  record Skipped(String role, String reason) {}

  static List<MasterReview.Read> reads(List<Role> roles, List<Judgement> judgements) {
    return IntStream.range(0, roles.size())
        .mapToObj(i -> new MasterReview.Read(roles.get(i).name(), judgements.get(i)))
        .toList();
  }

  /**
   * For a human: what the verdict rests on, advice and skipped roles included; nothing reads it
   * back.
   */
  static String of(String taskId, List<MasterReview.Read> reads, List<Skipped> skipped) {
    Verdict verdict = Verdict.of(new Review(0, "", "", "", reads));
    StringBuilder file = new StringBuilder("# " + taskId + " — master review\n\n");
    skipped.forEach(role ->
        file.append("# skipped [")
            .append(role.role())
            .append("] ")
            .append(role.reason())
            .append('\n'));
    verdict
        .advice()
        .forEach(said -> file.append("# advice ").append(said.line().substring(2)).append('\n'));
    reads.stream()
        .filter(read -> read.judgement().failure().isBlank())
        .forEach(read ->
            read.judgement().premises().stream()
                .filter(p -> p.provenBy().isBlank())
                .forEach(p ->
                    file.append("# unproven [")
                        .append(read.role())
                        .append("] ")
                        .append(OneLine.of(p.claim()))
                        .append('\n')));
    verdict.findings().forEach(said -> file.append(said.line()).append('\n'));
    return switch (verdict.kind()) {
      case QUESTION -> {
        verdict.questions().stream()
            .skip(1)
            .forEach(q -> file.append("- also asked: ").append(q).append('\n'));
        yield file.append(verdict.question()).append("\n\nVERDICT: question\n").toString();
      }
      case NOT_READY -> file.append("\nVERDICT: not ready\n").toString();
      case READY -> file.append("nothing wrong\n\nVERDICT: ready\n").toString();
    };
  }
}
