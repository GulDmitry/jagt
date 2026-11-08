package dev.jagt.orchestrator.adapter.tracker;

import dev.jagt.orchestrator.adapter.tracker.MarkdownItems.Item;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.TrackerAssistant;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Answers for the keys the configured file holds; any other reference goes to the assistant that
 * would have answered without it.
 */
@Component
@Primary
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "orchestrator.tracker.workflow", havingValue = FileWorkflow.ID)
public class FileTrackerAssistant implements TrackerAssistant {

  private final TrackerAssistant elsewhere;
  private final ConfigService configService;

  @Override
  public Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections) {
    if (!isKey(ticketRef)) {
      return elsewhere.readTicket(ticketRef, corrections);
    }
    Path file = file();
    return items(file)
        .map(
            items ->
                MarkdownItems.find(items, ticketRef)
                    .map(item -> fact(item, file))
                    .orElseGet(() -> elsewhere.readTicket(ticketRef, corrections)))
        .orElseGet(Answer::unavailable);
  }

  @Override
  public Answer<String> readTicketText(String ticketRef) {
    if (!isKey(ticketRef)) {
      return elsewhere.readTicketText(ticketRef);
    }
    return items(file())
        .map(
            items ->
                MarkdownItems.find(items, ticketRef)
                    .map(item -> new Answer<>(Optional.of(item.text()), TokenUsage.NONE))
                    .orElseGet(() -> elsewhere.readTicketText(ticketRef)))
        .orElseGet(Answer::unavailable);
  }

  @Override
  public Answer<List<String>> findCandidates(String query) {
    return items(file())
        .map(
            items ->
                new Answer<>(
                    Optional.of(items.stream().map(Item::key).toList()), TokenUsage.NONE))
        .orElseGet(Answer::unavailable);
  }

  private static boolean isKey(String ref) {
    return ref != null && MarkdownItems.KEY.matcher(ref.strip().toUpperCase(Locale.ROOT)).matches();
  }

  private Path file() {
    String configured = configService.load().tracker().file();
    return Path.of(configured == null ? "" : configured).toAbsolutePath().normalize();
  }

  private static Answer<TicketFacts> fact(Item item, Path file) {
    TicketFacts facts =
        TicketFacts.defaults()
            .withExists(true)
            .withKey(item.key())
            .withTitle(item.title())
            .withTrackerStatus(item.status())
            .withUrl("file://" + file + "#" + item.key());
    return new Answer<>(Optional.of(facts), TokenUsage.NONE);
  }

  private static Optional<List<Item>> items(Path file) {
    try {
      return Optional.of(MarkdownItems.parse(Files.readString(file)));
    } catch (IOException | RuntimeException e) {
      log.atWarn()
          .setMessage("tracker file unreadable")
          .addKeyValue("path", file.toString())
          .addKeyValue("cause", Causes.of(e))
          .addKeyValue("effect", "read unavailable")
          .setCause(e)
          .log();
      return Optional.empty();
    }
  }
}
