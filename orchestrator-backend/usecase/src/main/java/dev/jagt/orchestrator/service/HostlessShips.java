package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.RequestlessShip;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Read when asked, from the configuration and the worktrees: nothing is recorded that a later round or a config change could outlive. */
@Service
@RequiredArgsConstructor
public class HostlessShips implements RequestlessShip {

    private final ConfigService configService;
    private final WorktreeChanges changes;

    @Override
    public boolean stands(TaskState task) {
        if (task.status() != TaskStatus.REVIEW_PENDING || task.repos().isEmpty()) {
            return false;
        }
        ConfigService.ConfigFile config = configService.load();
        return task.repos().stream().allMatch(repo -> config.hasNoCodeHost(Objects.requireNonNull(repo.project())))
                && !changes.anythingNewToShip(task);
    }
}
