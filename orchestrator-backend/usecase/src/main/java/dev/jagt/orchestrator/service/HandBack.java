package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.protocol.PlanCriteria;
import dev.jagt.orchestrator.protocol.PlanProbes;
import dev.jagt.orchestrator.protocol.PlanRepositories;
import dev.jagt.orchestrator.protocol.Violation;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.Probe;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** What the worktree says about a round being handed back, which is what a report cannot be believed about. */
@Service
@RequiredArgsConstructor
public class HandBack {

    private final WorktreeChanges worktreeChanges;
    private final Rounds rounds;
    private final ConfigService configService;

    public boolean anyUncommitted(TaskState task) {
        return worktreeChanges.anyUncommitted(task);
    }

    public String pushedTips(TaskState task) {
        return worktreeChanges.pushedTips(task);
    }

    public RoundState round(TaskState task) {
        return rounds.of(task);
    }

    /** Whether this hand-back still owes jagt a verification run, which holds it at VERIFYING. */
    public boolean verificationOwed(TaskState task) {
        return !task.probes().isEmpty() || task.repos().stream()
                .anyMatch(repo -> !Verification.command(configService.load().projects().get(repo.project())).isEmpty());
    }

    public PlanProbes.Read planProbes(TaskState task) {
        Path plan = Path.of(task.worktreePath()).resolve(Artifact.PLAN.fileName());
        List<String> projects = task.projects();
        if (!Files.isRegularFile(plan)) {
            return planRead("", projects);
        }
        return WorktreeFiles.read(plan).map(text -> planRead(text, projects)).orElseGet(() -> new PlanProbes.Read(List.of(),
                List.of(new Violation(Artifact.PLAN.fileName(), "jagt could not read it; check its permissions"))));
    }

    private static PlanProbes.Read planRead(String plan, List<String> projects) {
        PlanProbes.Read probes = PlanProbes.of(plan);
        List<Violation> violations = Stream.of(probes.violations(), PlanCriteria.violationsIn(plan),
                PlanRepositories.violationsIn(plan, projects),
                foreignRepos(probes.probes(), projects.isEmpty() ? PlanRepositories.changedIn(plan) : projects))
                .flatMap(List::stream).toList();
        return new PlanProbes.Read(probes.probes(), violations);
    }

    private static List<Violation> foreignRepos(List<Probe> probes, List<String> projects) {
        return probes.stream().filter(probe -> probe.repo() != null && !projects.contains(probe.repo()))
                .map(probe -> new Violation("plan.md probes", "`" + probe + "` names a repository the task lacks; its repositories: "
                        + String.join(", ", projects)))
                .toList();
    }

    public boolean masterReads() {
        return configService.load().master().running();
    }
}
