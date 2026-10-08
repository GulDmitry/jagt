package dev.jagt.orchestrator.capability.ship;

import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.CapabilityInterceptor;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.Supplier;

/** The ship's commit is the session's, so the change is folded into the specs before the session is told to make it. */
@Component
@RequiredArgsConstructor
public class SpecFold implements CapabilityInterceptor {

    private final StateService stateService;
    private final Specs specs;

    @Override
    public TaskAction action() {
        return TaskAction.SHIP;
    }

    @Override
    public Outcome around(String taskId, Supplier<Outcome> work) {
        stateService.task(taskId).ifPresent(task -> task.repos().stream().map(TaskRepo::worktreePath)
                .forEach(worktree -> specs.fold(Path.of(worktree), taskId)));
        return work.get();
    }
}
