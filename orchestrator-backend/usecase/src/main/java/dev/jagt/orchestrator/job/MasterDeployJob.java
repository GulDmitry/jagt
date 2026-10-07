package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.MasterRight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In act, a request green with every thread closed is deployed as the human's own press would. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDeployJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final CommandService commands;
    private final Map<String, Long> pressed = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "master-deploy";
    }

    @Override
    public String describe() {
        return "deploy each task whose request is green with every thread closed";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        if (!configService.load().master().may(MasterRight.DEPLOY)) {
            return;
        }
        stateService.tasks().forEach((taskId, task) -> {
            // Once per arrival, or a refused deploy is pressed again every tick.
            if (!FlowRules.deployedByTheMaster(task.status())
                    || Long.valueOf(task.statusSince()).equals(pressed.put(taskId, task.statusSince()))) {
                return;
            }
            log.atInfo().setMessage("master deploys").addKeyValue("task", taskId).log();
            OriginContext.as(ActionOrigin.MASTER, () -> commands.execute(taskId, TaskAction.DEPLOY));
        });
    }
}
