package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which repository an item's work belongs in. A board carries items for every repository at once and a label
 * naming a layer places none of them, so what decides is a read of the item itself — against what each
 * repository is, and against where items like it were actually done.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectRouting {

    /** What fits in a prompt without drowning the item being placed; the newest, an install's layout drifting. */
    private static final int PRECEDENTS = 40;

    private final ConfigService configService;
    private final MeteredAssistant assistant;
    private final FinishedTasks finished;
    private final RoutingMemory memory;

    /** Empty where nothing could place it, which is a human's to settle rather than anything's to guess. */
    public Optional<String> projectFor(TicketFacts item) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        // Nothing to confirm where there is nothing to choose between.
        if (projects.size() == 1) {
            return Optional.of(projects.keySet().iterator().next());
        }
        List<String> suggested = projectsMatching(item, labelsOf(projects));
        return asked(item, projects, suggested, RoutingQuestion.defaults()
                .withItem(item)
                .withProjects(aboutEach(projects))
                .withSuggested(suggested)
                .withPrecedents(precedents())
                .withRules(memory.rules()));
    }

    private Optional<String> asked(TicketFacts item, Map<String, ProjectConfig> projects,
                                   List<String> suggested, RoutingQuestion question) {
        Answer<RoutingAnswer> answer = assistant.routeProject(question);
        if (answer.facts().isEmpty()) {
            log.atError().setMessage("project routing unreadable")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("cause", "the router answered nothing, so the item was left for a human")
                    .log();
            return Optional.empty();
        }
        RoutingAnswer routed = answer.facts().get();
        String chosen = routed.project();
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
        learn(item, suggested, routed);
        return Optional.of(chosen);
    }

    /**
     * A rule is kept only where the labels did NOT already place the item: what the machine can work out is
     * not worth a line, and a memory full of the obvious is one nobody reads.
     */
    private void learn(TicketFacts item, List<String> suggested, RoutingAnswer routed) {
        if (!routed.teaches() || suggested.contains(routed.project())) {
            return;
        }
        if (memory.remember(routed.rule(), routed.project())) {
            log.atInfo().setMessage("routing memory written")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("rule", routed.rule())
                    .addKeyValue("project", routed.project())
                    .log();
        }
    }

    /**
     * Where items like this one were done, read off the record jagt already keeps. Only what a human chose or
     * a deploy confirmed: a routing nothing checked would come back as a lesson taught by the router itself.
     */
    private List<String> precedents() {
        List<FinishedTask> learnable = finished.all().stream()
                .filter(FinishedTask::routingWorthLearningFrom)
                .toList();
        return learnable.stream()
                .skip(Math.max(0, learnable.size() - PRECEDENTS))
                .map(task -> "- " + task.id() + " \"" + task.title() + "\" → " + task.projects().get(0))
                .toList();
    }

    /** Every project a label of the item, or the tracker project it sits under, names outright. */
    public static List<String> projectsMatching(TicketFacts facts, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(facts.labels());
        tokens.add(facts.trackerProject());
        return projectLabels.entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .toList();
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
