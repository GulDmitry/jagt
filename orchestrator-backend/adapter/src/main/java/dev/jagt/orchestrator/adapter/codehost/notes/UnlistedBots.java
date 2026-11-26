package dev.jagt.orchestrator.adapter.codehost.notes;

import dev.jagt.orchestrator.port.codehost.review.AuthorKind;
import dev.jagt.orchestrator.port.codehost.review.RequestNote;
import dev.jagt.orchestrator.service.ConfigService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Warns once per account that {@code reviewReplyAuthors} names, the host typed a person and no bot
 * list holds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class UnlistedBots {

  private final ConfigService configService;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  void warn(String project, List<RequestNote> notes) {
    List<String> named = configService.load().codeReview().reviewReplyAuthorsOrEmpty();
    notes.stream()
        .filter(note -> note.kind() == AuthorKind.PERSON)
        .map(RequestNote::author)
        .filter(author -> named.stream().anyMatch(author::equalsIgnoreCase))
        .filter(author -> warned.add(author.toLowerCase(Locale.ROOT)))
        .forEach(author ->
            log.atWarn()
                .setMessage("reply author typed a person")
                .addKeyValue("project", project)
                .addKeyValue("author", author)
                .addKeyValue("cause", "the host does not flag it and no bot list names it")
                .addKeyValue("effect", "its notes read person, a round answering it waits")
                .addKeyValue("fix", "add it to codeReview.reviewBots")
                .log());
  }
}
