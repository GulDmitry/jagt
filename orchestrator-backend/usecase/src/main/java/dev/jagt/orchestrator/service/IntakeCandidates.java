package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TaskName;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the tracker says a machine may start. A search is a model's answer, so nothing it names is taken on trust:
 * every key is read back off the item itself and judged by the configured workflow, and a key jagt already holds
 * is never read at all.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeCandidates {

    /** Every candidate costs a read, so one poll buys a handful and whatever is left waits for the next. */
    private static final int READS_PER_POLL = 5;

    private final MeteredAssistant assistant;
    private final TicketReader tickets;
    private final TrackerWorkflow workflow;

    /** An item that may be started, and what learning that cost. */
    public record Ready(TicketFacts item, TokenUsage paid) {
    }

    /** Empty where nobody got to ask; an empty list means the tracker answered and nothing is waiting. */
    public Optional<List<Ready>> waiting(Set<String> held) {
        Answer<List<String>> found = assistant.findCandidates(workflow.candidateQuery());
        if (found.facts().isEmpty()) {
            log.atError().setMessage("intake search unreadable")
                    .addKeyValue("tracker", workflow.id())
                    .addKeyValue("cause", "the assistant reached no tracker, so nothing was taken this poll")
                    .log();
            return Optional.empty();
        }
        List<Ready> ready = new ArrayList<>();
        int reads = 0;
        for (String key : found.facts().get()) {
            if (held.contains(key)) {
                continue;
            }
            if (!TaskName.isTicketKey(key)) {
                log.atWarn().setMessage("intake search answered with a non-key")
                        .addKeyValue("ref", key)
                        .addKeyValue("cause", "nothing that is not an issue key is read")
                        .log();
                continue;
            }
            if (reads == READS_PER_POLL) {
                break;
            }
            reads++;
            eligible(key).ifPresent(ready::add);
        }
        return Optional.of(List.copyOf(ready));
    }

    private Optional<Ready> eligible(String key) {
        Answer<TicketFacts> read = tickets.read(key);
        Optional<TicketFacts> facts = read.facts().filter(TicketFacts::usable);
        if (facts.isEmpty()) {
            log.atWarn().setMessage("intake read unusable")
                    .addKeyValue("ref", key)
                    .addKeyValue("cause", read.facts().isEmpty() ? "the read never reached the tracker"
                            : "the item came back with no key, title or url")
                    .log();
            return Optional.empty();
        }
        TicketFacts item = facts.get();
        // A stage nobody could read is not a stage that fails to match, and starting work on the two alike is
        // how an item gets picked up because its tracker was down.
        if (item.trackerStatus().isBlank()) {
            log.atWarn().setMessage("intake stage unreadable")
                    .addKeyValue("ref", key)
                    .addKeyValue("cause", "the item came back with no workflow status")
                    .log();
            return Optional.empty();
        }
        return workflow.startsWork(item) ? Optional.of(new Ready(item, read.usage())) : Optional.empty();
    }
}
