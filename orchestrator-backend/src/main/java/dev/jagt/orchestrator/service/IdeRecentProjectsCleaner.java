package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.platform.EditorDriver;
import dev.jagt.orchestrator.platform.EditorDriver.WorktreeLocation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
public class IdeRecentProjectsCleaner {

    private final EditorDriver editorDriver;
    private final ConfigService configService;

    public IdeRecentProjectsCleaner(EditorDriver editorDriver, ConfigService configService) {
        this.editorDriver = editorDriver;
        this.configService = configService;
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanDeadWorktreeProjects() {
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
