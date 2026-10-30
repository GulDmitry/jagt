package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Standing;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.flow.TurnKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Once per start, a running session holding an open turn is asked to repeat its last report, which a restart may have lost. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestartAskJob implements TaskJob {

    static final String FACTS = "jagt was restarted.";

    private final AgentSessions sessions;
    private final StepRelay relay;

    @Override
    public String id() {
        return "restart-ask";
    }

    @Override
    public String describe() {
        return "ask each running session holding an open turn to repeat its last status report, once after a start";
    }

    @Override
    public @Nullable Duration every() {
        return null;
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withTurns(TurnKind.OPEN);
    }

    @Override
    public void run(String taskId, TaskState task) {
        ask(taskId, Standing.of(task));
    }

    private void ask(String taskId, Standing read) {
        if (!sessions.agentLive(taskId) || sessions.composing(taskId)) {
            log.atInfo().setMessage("restart ask skipped").addKeyValue("task", taskId)
                    .addKeyValue("cause", "no session is running, or the human is typing in it")
                    .addKeyValue("effect", "nothing said, the session is not revived for it").log();
            return;
        }
        try {
            relay.say(taskId, "restarted", FACTS, read);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("restart ask not sent").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.toString()).addKeyValue("effect", "the session was not asked").log();
        }
    }
}
