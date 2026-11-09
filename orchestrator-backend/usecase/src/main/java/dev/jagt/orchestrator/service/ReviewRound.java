package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CodeHost;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A request's round: the model's read of it, its note ids replaced by the host's own, and kept for
 * a poll that finds the request as that read left it.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewRound {

  private static final int KEPT = 64;

  private final ReviewReader reader;
  private final CodeHost codeHost;
  private final Map<String, LastRead> lastReads = Collections.synchronizedMap(new LastReads());

  public Answer<ReviewFacts> read(String taskId, String project, String reviewRequestUrl) {
    Optional<String> fingerprint = fingerprint(project, reviewRequestUrl);
    Optional<Answer<List<String>>> notesFirst =
        fingerprint.map(read -> codeHost.unresolvedNotes(project, reviewRequestUrl));
    Optional<String> mark =
        fingerprint.flatMap(
            read ->
                notesFirst
                    .flatMap(Answer::facts)
                    .map(ids -> read + "\n" + String.join(" ", ids)));
    Optional<ReviewFacts> kept = kept(reviewRequestUrl, mark);
    if (kept.isPresent()) {
      log.atInfo()
          .setMessage("review read kept")
          .addKeyValue("task", taskId)
          .addKeyValue("ref", reviewRequestUrl)
          .log();
      return new Answer<>(kept, TokenUsage.NONE);
    }
    lastReads.remove(reviewRequestUrl);
    Answer<ReviewFacts> answer = reader.read(taskId, reviewRequestUrl);
    Optional<ReviewFacts> read = answer.facts();
    if (read.isEmpty() || !read.get().request().exists()) {
      return answer;
    }
    Answer<List<String>> notes =
        notesFirst.orElseGet(() -> codeHost.unresolvedNotes(project, reviewRequestUrl));
    if (notes.facts().isEmpty()) {
      log.atWarn()
          .setMessage("note ids not read")
          .addKeyValue("ref", reviewRequestUrl)
          .addKeyValue("cause", notes.failure())
          .log();
    }
    ReviewFacts facts = read.get().withNewestNotes(notes.facts().orElse(List.of()));
    mark.ifPresent(said -> lastReads.put(reviewRequestUrl, new LastRead(said, facts)));
    return new Answer<>(
        Optional.of(facts), answer.usage(), answer.failure(), answer.refused(), answer.reach());
  }

  private Optional<ReviewFacts> kept(String reviewRequestUrl, Optional<String> mark) {
    if (mark.isEmpty() || OriginContext.current() != ActionOrigin.AUTO_REVIEW) {
      return Optional.empty();
    }
    return Optional.ofNullable(lastReads.get(reviewRequestUrl))
        .filter(last -> last.mark().equals(mark.get()))
        .map(LastRead::facts);
  }

  private Optional<String> fingerprint(String project, String reviewRequestUrl) {
    Answer<String> read = codeHost.requestFingerprint(project, reviewRequestUrl);
    if (read.facts().isEmpty() && !read.failure().isBlank()) {
      log.atWarn()
          .setMessage("request fingerprint not read")
          .addKeyValue("ref", reviewRequestUrl)
          .addKeyValue("cause", read.failure())
          .addKeyValue("effect", "the request is read by the model")
          .log();
    }
    return read.facts().filter(said -> !said.isBlank());
  }

  private record LastRead(String mark, ReviewFacts facts) {}

  private static final class LastReads extends LinkedHashMap<String, LastRead> {

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, LastRead> eldest) {
      return size() > KEPT;
    }
  }
}
