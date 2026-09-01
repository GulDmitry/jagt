package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Delivery happens off the caller's thread with a bounded backlog: a hung channel costs a banner, not a task. */
@Component
@Slf4j
public class Notifications {

    private static final int BACKLOG = 64;

    private final List<Notifier> channels;
    private final Executor delivery;

    @Autowired
    public Notifications(List<Notifier> channels) {
        this(channels, new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(BACKLOG),
                Thread.ofPlatform().name("jagt-notify-", 0).daemon().factory()));
    }

    Notifications(List<Notifier> channels, Executor delivery) {
        this.channels = List.copyOf(channels);
        this.delivery = delivery;
    }

    public void send(Notification notification) {
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
