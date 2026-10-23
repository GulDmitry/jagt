package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.MasterRight;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In act, the repository a session asked for is attached as the human's own press would. */
@Service
@RequiredArgsConstructor
public class MasterRepoJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final MasterPress press;
    private final Map<String, String> pressed = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "master-repo";
    }

    @Override
    public Serves serves() {
        return Serves.MASTER;
    }

    @Override
    public String describe() {
        return "attach the repository a session asked for, where the Master stands in for the human";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(2);
    }

    @Override
    public void run() {
        if (!configService.load().master().may(MasterRight.REPO)) {
            return;
        }
        stateService.tasks().forEach((taskId, task) -> {
            String request = task.repoRequest();
            if (request == null) {
                pressed.remove(taskId);
                return;
            }
            // Once per request, or a refused press is repeated every tick.
            if (task.status() == TaskStatus.DONE || task.loopStopped() || request.equals(pressed.put(taskId, request))) {
                return;
            }
            press.execute(taskId, TaskAction.ADD_REPO);
        });
    }
}
