package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.UserNotifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

/** A task notification links to the board narrowed to that task, through the filter the page already has. */
@Component
public class DesktopNotifier implements Notifier {

    private final UserNotifier os;
    private final String boardUrl;

    public DesktopNotifier(UserNotifier os, @Value("${server.port:8290}") String port) {
        this.os = os;
        this.boardUrl = "http://localhost:" + port + "/";
    }

    @Override
    public String id() {
        return "desktop";
    }

    @Override
    public void deliver(Notification notification) {
        os.notify(title(notification), notification.body(), link(notification.taskId()));
    }

    private @Nullable String link(@Nullable String taskId) {
        return taskId == null ? null
                : boardUrl + "?task=" + java.net.URLEncoder.encode(taskId, java.nio.charset.StandardCharsets.UTF_8);
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
