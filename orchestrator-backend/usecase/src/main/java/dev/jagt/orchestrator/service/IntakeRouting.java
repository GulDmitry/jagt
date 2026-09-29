package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which repository an item's work belongs in. The cheap answers come first and cost nothing — one configured
 * project, or labels naming exactly one — and only an item they cannot place is worth a model call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeRouting {

    private final ConfigService configService;
    private final MeteredAssistant assistant;

    /** Empty where nothing could place it, which is a human's to settle rather than anything's to guess. */
    public Optional<String> projectFor(TicketFacts item) {
        Set<String> keys = configService.load().projects().keySet();
        if (keys.size() == 1) {
            return Optional.of(keys.iterator().next());
        }
        List<String> byLabel = TaskLauncher.projectsMatching(item, labelsByProject());
        return byLabel.size() == 1 ? Optional.of(byLabel.get(0)) : asked(item, keys);
    }

    private Optional<String> asked(TicketFacts item, Set<String> keys) {
        Answer<String> answer = assistant.routeProject(item, keys);
        if (answer.facts().isEmpty()) {
            log.atError().setMessage("project routing unreadable")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("cause", "the router answered nothing, so the item was left for a human")
                    .log();
            return Optional.empty();
        }
        String chosen = answer.facts().get();
        // A key outside the configured set is a read that failed in the shape of an answer.
        if (ProjectRead.NONE.equals(chosen) || !keys.contains(chosen)) {
            log.atInfo().setMessage("project routing undecided")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("answered", chosen)
                    .log();
            return Optional.empty();
        }
        return Optional.of(chosen);
    }

    private Map<String, List<String>> labelsByProject() {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        configService.load().projects().forEach((key, project) -> labels.put(key, project.labels()));
        return labels;
    }
}
