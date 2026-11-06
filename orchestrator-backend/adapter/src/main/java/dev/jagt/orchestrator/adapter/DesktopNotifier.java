package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.BoardUrl;
import dev.jagt.orchestrator.service.ConfigService;

import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

/** A task notification links to the board narrowed to that task, through the filter the page already has. */
@Component
public class DesktopNotifier implements Notifier {

    private final UserNotifier os;
    private final ConfigService config;
    private final BoardUrl boardUrl;

    public DesktopNotifier(UserNotifier os, ConfigService config, BoardUrl boardUrl) {
        this.os = os;
        this.config = config;
        this.boardUrl = boardUrl;
    }

    @Override
    public String id() {
        return "desktop";
    }

    @Override
    public boolean takes(Notification notification) {
        return config.load().notifications().desktopEvents().contains(notification.event());
    }

    @Override
    public void deliver(Notification notification) {
        os.notify(title(notification), notification.body(), link(notification.taskId()));
    }

    private @Nullable String link(@Nullable String taskId) {
        return taskId == null ? null : boardUrl.ofTask(taskId);
    }

    private static String title(Notification notification) {
        String subject = notification.title() == null || notification.title().isBlank()
                ? notification.topic().name().toLowerCase(java.util.Locale.ROOT) : notification.title();
        if (notification.taskId() == null) {
            return "jagt · " + subject;
        }
        String alias = notification.alias() == null || notification.alias().isBlank() ? "" : notification.alias() + " · ";
        return "jagt · " + alias + notification.taskId() + " · " + subject;
    }
}
