package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.EditorDriver;
import dev.jagt.orchestrator.port.EditorDriver.WorktreeLocation;
import lombok.RequiredArgsConstructor;
import dev.jagt.orchestrator.job.Job;
import org.springframework.stereotype.Service;

import java.time.Duration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the editor's recent-projects list free of closed tasks. {@link EditorDriver#forgetProject} runs while the
 * IDE is live and is clobbered when it next flushes its in-memory list, so the removal is re-run on a schedule and
 * sticks on the first tick the IDE is closed. Scoped per configured project, so real projects are never pruned.
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
