package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.RoutingAssistant;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FinishedTask;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
import dev.jagt.orchestrator.task.RulePair;
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
 * Which repositories an item's work belongs in: every project its labels name, else a read of the item itself —
 * against what each repository is, and against where items like it were actually done.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectRouting {

    /** What fits in a prompt without drowning the item being placed; the newest, an install's layout drifting. */
    private static final int PRECEDENTS = 40;

    private final ConfigService configService;
    private final RoutingAssistant assistant;
    private final FinishedTasks finished;
    private final RoutingMemory memory;

    /** A router that answered nothing has not said the item fits nowhere: only {@link Undecided} has. */
    public sealed interface Placement {

        default List<String> projects() {
            return this instanceof Placed placed ? placed.keys() : List.of();
        }

        default String reason() {
            return switch (this) {
                case Placed placed -> "placed in " + String.join(", ", placed.keys());
                case Undecided undecided -> undecided.why();
                case Unreadable unreadable -> unreadable.cause();
            };
        }
    }

    public record Placed(List<String> keys) implements Placement {
    }

    public record Undecided(String why) implements Placement {
    }

    public record Unreadable(String cause) implements Placement {
    }

    public Placement projectFor(TicketFacts item) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        // Nothing to confirm where there is nothing to choose between.
        if (projects.size() == 1) {
            return new Placed(List.of(projects.keySet().iterator().next()));
        }
        List<String> labelled = projectsMatching(item, labelsOf(projects));
        if (!labelled.isEmpty()) {
            return new Placed(labelled);
        }
        return asked(item, projects, RoutingQuestion.defaults()
                .withItem(item)
                .withProjects(aboutEach(projects))
                .withPrecedents(precedents())
                .withRules(memory.rules()));
    }

    private Placement asked(TicketFacts item, Map<String, ProjectConfig> projects, RoutingQuestion question) {
        Answer<RoutingAnswer> answer = assistant.routeProject(question);
        if (answer.facts().isEmpty()) {
            log.atWarn().setMessage("project routing unreadable")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("cause", "the router answered nothing")
                    .addKeyValue("effect", "retried next poll")
                    .log();
            return new Unreadable("the router answered nothing");
        }
        RoutingAnswer routed = answer.facts().get();
        String chosen = routed.project();
        // A key outside the configured set is a read that failed in the shape of an answer.
        if (ProjectRead.NONE.equals(chosen) || !projects.containsKey(chosen)) {
            log.atInfo().setMessage("project routing undecided")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("answered", chosen)
                    .log();
            return new Undecided("the router read it and placed it in no configured project (answered '"
                    + chosen + "')");
        }
        learn(item, routed);
        return new Placed(List.of(chosen));
    }

    /**
     * A human placing an item where the router had not is the world contradicting what is written down, and
     * the only correction nothing else here carries. Nothing is asked unless a rule exists AND the router had
     * placed this very item somewhere else: retiring a rule that was never wrong costs more than leaving it.
     */
    public void placedByHand(String ticketKey, String project) {
        List<String> rules = memory.rules();
        if (rules.isEmpty() || !routerPlacedElsewhere(ticketKey, project)) {
            return;
        }
        Answer<String> answer = assistant.staleRule(ticketKey, project, rules);
        if (answer.facts().isEmpty()) {
            log.atWarn().setMessage("stale rule unreadable")
                    .addKeyValue("ref", ticketKey)
                    .addKeyValue("cause", "nothing answered, so every rule stands")
                    .log();
            return;
        }
        String stale = answer.facts().get();
        if (!rules.contains(stale)) {
            return;
        }
        if (memory.retire(stale)) {
            log.atInfo().setMessage("routing rule retired")
                    .addKeyValue("ref", ticketKey)
                    .addKeyValue("rule", stale)
                    .addKeyValue("placedIn", project)
                    .log();
        }
    }

    /** Only the router's own placements can be contradicted: what a human did before was never a rule's doing. */
    private boolean routerPlacedElsewhere(String ticketKey, String project) {
        return finished.all().stream()
                .filter(task -> task.id().equals(ticketKey))
                .anyMatch(task -> task.openedBy() == ActionOrigin.TRACKER
                        && !task.projects().contains(project));
    }

    private void learn(TicketFacts item, RoutingAnswer routed) {
        if (!routed.teaches()) {
            return;
        }
        if (memory.remember(routed.rule(), routed.project())) {
            log.atInfo().setMessage("routing memory written")
                    .addKeyValue("ref", item.key())
                    .addKeyValue("rule", routed.rule())
                    .addKeyValue("project", routed.project())
                    .log();
            if (memory.full()) {
                mergeSecondWording();
            }
        }
    }

    /** At the ceiling a second wording would push out a rule that works; only a read of the whole file sees it. */
    private void mergeSecondWording() {
        Optional<RulePair> pair = assistant.sameRules(memory.rules()).facts();
        if (pair.isPresent() && memory.merge(pair.get().kept(), pair.get().duplicate())) {
            log.atInfo().setMessage("routing rules merged")
                    .addKeyValue("rule", pair.get().kept())
                    .addKeyValue("merged", pair.get().duplicate())
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
