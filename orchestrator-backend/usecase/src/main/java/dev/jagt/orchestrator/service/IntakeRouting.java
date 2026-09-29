package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which repository an item's work belongs in. A board carries items for every repository at once and a label
 * naming a layer places none of them, so the labels are a suggestion to be checked rather than the answer: what
 * decides is a read of the item itself, against what each repository is.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntakeRouting {

    private final ConfigService configService;
    private final MeteredAssistant assistant;

    /** Empty where nothing could place it, which is a human's to settle rather than anything's to guess. */
    public Optional<String> projectFor(TicketFacts item) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        // Nothing to confirm where there is nothing to choose between.
        if (projects.size() == 1) {
            return Optional.of(projects.keySet().iterator().next());
        }
        return asked(item, projects, TaskLauncher.projectsMatching(item, labelsOf(projects)));
    }

    private Optional<String> asked(TicketFacts item, Map<String, ProjectConfig> projects,
                                   List<String> suggested) {
        Answer<String> answer = assistant.routeProject(item, aboutEach(projects), suggested);
        if (answer.facts().isEmpty()) {
            log.atError().setMessage("project routing unreadable")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("cause", "the router answered nothing, so the item was left for a human")
                    .log();
            return Optional.empty();
        }
        String chosen = answer.facts().get();
        // A key outside the configured set is a read that failed in the shape of an answer.
        if (ProjectRead.NONE.equals(chosen) || !projects.containsKey(chosen)) {
            log.atInfo().setMessage("project routing undecided")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("answered", chosen)
                    .addKeyValue("suggested", String.join(",", suggested))
                    .log();
            return Optional.empty();
        }
        if (!suggested.isEmpty() && !suggested.contains(chosen)) {
            log.atInfo().setMessage("project routing overruled the labels")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("suggested", String.join(",", suggested))
                    .addKeyValue("chose", chosen)
                    .log();
        }
        return Optional.of(chosen);
    }

    private static Map<String, List<String>> labelsOf(Map<String, ProjectConfig> projects) {
        Map<String, List<String>> labels = new LinkedHashMap<>();
        projects.forEach((key, project) -> labels.put(key, project.labels()));
        return labels;
    }

    private static Map<String, String> aboutEach(Map<String, ProjectConfig> projects) {
        Map<String, String> about = new LinkedHashMap<>();
        projects.forEach((key, project) -> about.put(key, project.aboutOrPath()));
        return about;
    }
}
