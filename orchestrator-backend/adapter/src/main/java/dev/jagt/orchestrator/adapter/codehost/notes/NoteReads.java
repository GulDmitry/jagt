package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.adapter.codehost.cli.HostCli;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.Reaction;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** What the CLI drivers of the review port share: who wrote a note, and one read per note. */
final class NoteReads {

  private NoteReads() {}

  /**
   * The account jagt acts as first, then what the host calls a bot, then the project's own list;
   * never a guess from a name.
   */
  static AuthorKind kind(String author, String me, boolean hostSaysBot, List<String> bots) {
    if (!me.isBlank() && author.equalsIgnoreCase(me)) {
      return AuthorKind.ACTOR;
    }
    boolean listed = bots.stream().anyMatch(author::equalsIgnoreCase);
    return hostSaysBot || listed ? AuthorKind.BOT : AuthorKind.PERSON;
  }

  /** One read per id; the first that fails is the failure of all, never a map missing an id. */
  static Answer<Map<String, Reaction>> eachNote(
      List<String> noteIds, Function<String, Answer<Reaction>> read) {
    Map<String, Reaction> reactions = new LinkedHashMap<>();
    for (String id : noteIds) {
      Answer<Reaction> one = read.apply(id);
      if (one.facts().isEmpty()) {
        return HostCli.failureOf(one);
      }
      reactions.put(id, one.facts().get());
    }
    return new Answer<>(Optional.of(reactions), TokenUsage.NONE);
  }
}
