package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.StopHold;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** What a newly tripped stop tells: the log, the human, the journal; a stop the human set, or the Master holds, tells the human nothing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StopAnnouncements {

    private final Notifications notifications;
    private final StopLog journal;

    public void announce(String taskId, TaskState task, TaskStop stop, StopHold hold) {
        if (stop.trigger().ends() == TaskStop.Trigger.Ends.PAUSE) {
            return;
        }
        String alias = task.alias() == null ? taskId : task.alias();
        String trigger = stop.trigger().name();
        log.atWarn().setMessage("task stopped").addKeyValue("task", taskId).addKeyValue("alias", alias)
                .addKeyValue("cause", trigger.toLowerCase(Locale.ROOT))
                .addKeyValue("said", OneLine.clipped(stop.evidence()))
                .addKeyValue("effect", "auto-review and the master leave the task")
                .addKeyValue("fix", "lift " + alias)
                .log();
        if (hold != StopHold.MASTER) {
            notifications.send(new Notification(Notification.Topic.INSTALL, Notification.Event.STOPPED, taskId, null, "jagt stopped a task", stop.reading()));
        }
        journal.record(new StopLog.Stop(stop.at(), taskId, alias, trigger, task.status(), stop.evidence()));
    }
}
