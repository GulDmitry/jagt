package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reads a state file written before the deployment held every deploy fact into the deployment, once. */
final class LegacyDeployment {

    private LegacyDeployment() {
    }

    /** A repository as an old state file wrote it, with the merge commit and fixes it then kept on the repository. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repo(@Nullable String project, @Nullable String worktreePath, @Nullable String remoteUrl,
                @Nullable String mrUrl, @Nullable String deployCommit, @Nullable Boolean merged,
                @Nullable Boolean hostMerged, @Nullable List<String> deployFixes) {

        TaskRepo repo() {
            return new TaskRepo(project, worktreePath, remoteUrl, mrUrl, merged, hostMerged);
        }

        boolean landed() {
            return deployCommit != null && !deployCommit.isBlank();
        }
    }

    /** The watch an old state file kept on the task, which named the deploy worktree the conflict stood in. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Watch(long conflictSince, @Nullable String worktree, Conflict.@Nullable Progress progress, long movedAt,
                 Conflict.@Nullable Told told) {
    }

    static @Nullable Deployment of(TaskState task, List<Repo> stored, @Nullable Watch watch) {
        boolean unrecorded = task.deployment() == null;
        Deployment deployment = unrecorded ? fromRepos(task, stored) : task.deployment();
        if (task.status() != TaskStatus.DEPLOY_CONFLICT) {
            return deployment;
        }
        Optional<Deployment.Target> recorded = Optional.ofNullable(deployment).flatMap(Deployment::conflictedTarget);
        if (recorded.isPresent() && watch == null) {
            return deployment;
        }
        Deployment.Cause cause = recorded.map(target -> Objects.requireNonNull(target.conflict()).cause())
                .orElse(DeployRedLine.marks(task.message()) ? Deployment.Cause.RED_PIPELINE : Deployment.Cause.MERGE);
        Conflict carried = watch == null ? Conflict.of(cause, task.lastActiveTimestamp())
                : new Conflict(cause, watch.conflictSince(), watch.progress(), watch.movedAt(), watch.told(), null);
        if (recorded.isPresent()) {
            return Objects.requireNonNull(deployment).withConflict(standing -> carried);
        }
        Deployment base = deployment != null ? deployment : Deployment.defaults().withPressedAt(carried.since())
                .withTargets(task.repos().stream().map(repo -> Deployment.Target.of(nameOf(repo), "")).toList());
        int placed = unrecorded ? placed(task.repos(), watch) : -1;
        int at = placed >= 0 ? placed : base.at() < base.targets().size() ? base.at() : 0;
        return base.withAt(at).withTarget(base.targets().get(at).project(), target -> target.withConflict(carried));
    }

    private static @Nullable Deployment fromRepos(TaskState task, List<Repo> stored) {
        if (stored.stream().noneMatch(Repo::landed)) {
            return null;
        }
        long since = task.statusSince();
        List<Deployment.Target> targets = stored.stream().map(repo -> target(repo, since)).toList();
        long landed = targets.stream().takeWhile(Deployment.Target::landed).count();
        return new Deployment(since, targets, task.status() == TaskStatus.DEPLOY_CONFLICT ? (int) landed : targets.size(),
                task.turnOrNone().mark(Step.DEPLOY.id()));
    }

    private static Deployment.Target target(Repo repo, long since) {
        Deployment.Target target = Deployment.Target.of(repo.project() == null ? "" : repo.project(), "");
        if (!repo.landed()) {
            return target;
        }
        target = target.merged(Objects.requireNonNull(repo.deployCommit())).withPushedAt(since);
        for (String fix : repo.deployFixes() == null ? List.<String>of() : repo.deployFixes()) {
            target = target.withFix(fix);
        }
        return target;
    }

    /** The repositories of the file, or the one its pre-repository shape kept at the top level. */
    static List<Repo> stored(@Nullable List<Repo> repos, Repo topLevel) {
        return repos != null && !repos.isEmpty() ? repos : List.of(topLevel);
    }

    /**
     * The repository whose deploy worktree the old watch named: that worktree sits beside the repository, as does the
     * task's own worktree in it. -1 where nothing matches.
     */
    private static int placed(List<TaskRepo> repos, @Nullable Watch watch) {
        if (watch == null || watch.worktree() == null) {
            return -1;
        }
        Path stood = Path.of(watch.worktree()).getParent();
        for (int i = 0; i < repos.size(); i++) {
            String worktree = repos.get(i).worktreePath();
            if (worktree != null && Objects.equals(stood, Path.of(worktree).getParent())) {
                return i;
            }
        }
        return -1;
    }

    private static String nameOf(TaskRepo repo) {
        return repo.project() == null ? "" : repo.project();
    }
}
