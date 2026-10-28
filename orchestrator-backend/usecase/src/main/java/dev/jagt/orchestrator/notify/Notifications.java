package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.task.TaskState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Delivery happens off the caller's thread with a bounded backlog: a hung channel costs a banner, not a task. */
@Component
@Slf4j
public class Notifications {

    private final List<Notifier> channels;
    private final TaskStore tasks;
    private final Executor delivery;

    public Notifications(List<Notifier> channels, TaskStore tasks, @Qualifier("notificationDelivery") Executor delivery) {
        this.channels = List.copyOf(channels);
        this.tasks = tasks;
        this.delivery = delivery;
    }

    public void send(Notification sent) {
        Notification notification = sent.taskId() == null ? sent : sent.withAlias(aliasOf(sent.taskId()));
        for (Notifier channel : channels) {
            if (!channel.takes(notification)) {
                continue;
            }
            try {
                delivery.execute(() -> deliver(channel, notification));
            } catch (RejectedExecutionException e) {
                log.atWarn().setMessage("notification dropped")
                        .addKeyValue("channel", channel.id())
                        .addKeyValue("said", notification.title())
                        .addKeyValue("cause", "channel backed up")
                        .log();
            }
        }
    }

    private @Nullable String aliasOf(String taskId) {
        return tasks.task(taskId).map(TaskState::alias).orElse(null);
    }

    private static void deliver(Notifier channel, Notification notification) {
        try {
            channel.deliver(notification);
        } catch (Throwable t) {
            log.atWarn().setMessage("notification delivery failed")
                    .addKeyValue("channel", channel.id())
                    .addKeyValue("said", notification.title())
                    .addKeyValue("cause", t.toString())
                    .log();
        }
    }
}
