package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.UserNotifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The banner, and WHERE IT PUTS THE HUMAN when they click it: a notification about a task links to the board
 * narrowed to that task, so one click leaves them looking at the card with its actions rather than at a list of
 * every task they have. It is the filter the page already has, not a second way to address a card.
 *
 * <p>No link when the board is not being served ({@code orchestrator.ui=tui}) or the notification is about the
 * install rather than a task: a click that opens a dead page is worse than one that does nothing.
 */
@Component
public class DesktopNotifier implements Notifier {

    private final UserNotifier os;
    private final String boardUrl;

    public DesktopNotifier(UserNotifier os,
                           @Value("${orchestrator.ui:web}") String ui,
                           @Value("${server.port:8290}") String port) {
        this.os = os;
        this.boardUrl = ui.contains("tui") && !ui.contains("both") ? null : "http://localhost:" + port + "/";
    }

    @Override
    public String id() {
        return "desktop";
    }

    @Override
    public void deliver(Notification notification) {
        os.notify(title(notification), notification.body(), link(notification.taskId()));
    }

    private String link(String taskId) {
        return taskId == null || boardUrl == null ? null
                : boardUrl + "?task=" + java.net.URLEncoder.encode(taskId, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String title(Notification notification) {
        String subject = notification.title() == null || notification.title().isBlank()
                ? notification.topic().name().toLowerCase(java.util.Locale.ROOT) : notification.title();
        return notification.taskId() == null ? "jagt · " + subject
                : "jagt · " + notification.taskId() + " · " + subject;
    }
}
