package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** A resolution staged in full finishes the deploy the human pressed, as the Master. */
@Component
@RequiredArgsConstructor
@Slf4j
class ConflictFinish {

    private final CommandService commands;
    private final ConfigService config;

    void deploy(String taskId, Path worktree) {
        log.atInfo().setMessage("master finishes deploy").addKeyValue("task", taskId)
                .addKeyValue("worktree", worktree).log();
        OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DEPLOY));
        config.startTaken();
    }
}
