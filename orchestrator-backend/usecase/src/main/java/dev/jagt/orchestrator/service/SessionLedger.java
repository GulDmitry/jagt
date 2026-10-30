package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

/** Writes what a session's harness reported as a sign on the task, and tells the human once when it raised an ask. */
@Component
@RequiredArgsConstructor
public class SessionLedger {

    private final StateService stateService;
    private final Notifications notifications;
    private final Clock clock;

    public void record(String taskId, @Nullable Path sessionLog, SessionSign sign) {
        if (sessionLog != null) {
            sign(taskId, new SessionSign.LogNamed(sessionLog.toString()));
        }
        sign(taskId, sign);
    }

    public void sign(String taskId, SessionSign sign) {
        long now = clock.millis();
        AtomicBoolean asked = new AtomicBoolean();
        stateService.updateTask(taskId, task -> {
            TaskState signed = TaskReducer.signed(task, sign, now);
            asked.set(!task.session().stampedAsk() && signed.session().stampedAsk());
            return signed;
        });
        if (asked.get() && sign instanceof SessionSign.Asked ask) {
            notifications.send(Notification.watchdog(taskId, "agent needs you",
                    ask.cause() == null ? "it waits at a prompt" : ask.cause()));
        }
    }
}
