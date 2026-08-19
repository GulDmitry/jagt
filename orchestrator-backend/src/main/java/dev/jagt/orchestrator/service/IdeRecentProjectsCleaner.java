package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.EditorDriver.WorktreeLocation;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the editor's recent-projects list free of {@code done} tasks. The per-{@code done}
 * {@link EditorDriver#forgetProject} prune runs while the IDE is live and gets clobbered when the IDE next
 * flushes its in-memory list, so the dead entry survives even a restart. This re-runs the removal on a
 * schedule: the first tick during which the IDE is closed makes it stick (the IDE won't re-add a project it
 * isn't opening). The scope — one {@link WorktreeLocation} per configured project — bounds the GC to jagt's
 * own worktrees so a human's real projects are never pruned.
 */
@Service
@RequiredArgsConstructor
public class IdeRecentProjectsCleaner implements Job {
    @Override
    public String id() {
        return "idecleanup";
    }

    @Override
    public String describe() {
        return "drop worktrees that no longer exist from the editor's recent-projects list";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }


    private final EditorDriver editorDriver;
    private final ConfigService configService;

    @Override
    public void run() {
        List<WorktreeLocation> locations = new ArrayList<>();
        configService.load().projects().forEach((projectKey, project) -> {
            Path parent = Path.of(project.path()).toAbsolutePath().normalize().getParent();
            if (parent != null) {
                locations.add(new WorktreeLocation(parent, projectKey));
            }
        });
        editorDriver.forgetDeadWorktrees(locations);
    }
}
