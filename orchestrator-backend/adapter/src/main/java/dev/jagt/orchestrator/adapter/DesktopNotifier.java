package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.port.Notifier;
import dev.jagt.orchestrator.port.UserNotifier;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesktopNotifier implements Notifier {

    private final UserNotifier os;

    @Override
    public String id() {
        return "desktop";
    }

    @Override
    public void deliver(Notification notification) {
        os.notify(title(notification), notification.body());
    }

    private static String title(Notification notification) {
        String subject = notification.title() == null || notification.title().isBlank()
                ? notification.topic().name().toLowerCase(java.util.Locale.ROOT) : notification.title();
        return notification.taskId() == null ? "jagt · " + subject
                : "jagt · " + notification.taskId() + " · " + subject;
    }
}
