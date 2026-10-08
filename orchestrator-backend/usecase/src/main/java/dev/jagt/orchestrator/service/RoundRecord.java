package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.notify.Notifications;
import dev.jagt.orchestrator.port.Notification;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Keeps what the host said about a round and taps the human ONCE when a run turns red: a later poll saying the same
 * thing writes nothing, or an unattended sweep would notify on a loop. ONE write, all three facts coming off one
 * read. A round that could not read them leaves the older round's word standing, flagged.
 */
@Service
@RequiredArgsConstructor
public class RoundRecord {

    private final StateService stateService;
    private final Notifications notifications;

    public void record(String taskId, ReviewFacts facts) {
        Optional<TaskState> before = stateService.task(taskId);
        String standing = before.map(TaskState::pipelineStatus).orElse(null);
        String read = orUnknown(facts.pipelineStatus());
        boolean unread = gotNoListing(facts.pipelineStatus());
        String checks = unread ? standing : read;
        boolean newChecks = !Objects.equals(standing, checks)
                || before.map(TaskState::pipelineUnread).orElse(false) != unread;
        boolean newApproval = !Objects.equals(before.map(TaskState::approved).orElse(null), facts.approved());
        boolean newOpened = facts.openedAt() > 0
                && before.map(TaskState::requestOpenedAt).orElse(0L) != facts.openedAt();
        if (!newChecks && !newApproval && !newOpened) {
            return;
        }
        stateService.updateTask(taskId, task -> (unread ? task.withChecksUnread() : task.withChecksRead(read))
                .withApproved(facts.approved()).withRequestOpenedAt(facts.openedAt()));
        Pipeline was = Pipeline.of(standing);
        Pipeline now = Pipeline.of(checks);
        if (newChecks && now.worthATap() && now != was) {
            notifications.send(Notification.checksFailed(taskId, checks));
        }
    }

    /**
     * Whether the round got no listing at all — the one word the reader writes for that. NOT the verdict:
     * {@code Pipeline.UNKNOWN} also covers a word the parser does not recognise, which IS a read.
     */
    private static boolean gotNoListing(String read) {
        return read == null || read.isBlank() || read.strip().equalsIgnoreCase("unknown");
    }

    /** A round that answered nothing still has to say so in a word, or every line quoting it renders a hole. */
    static String orUnknown(String read) {
        return read == null || read.isBlank() ? "unknown" : read;
    }
}
