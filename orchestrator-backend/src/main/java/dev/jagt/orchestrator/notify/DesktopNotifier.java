package dev.jagt.orchestrator.notify;

import dev.jagt.orchestrator.platform.UserNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The local desktop banner. It owns how a notification READS on a desktop — the `jagt` prefix and the task it is
 * about — so no caller assembles a title, and a second channel is free to word the same notification its own way.
 */
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
