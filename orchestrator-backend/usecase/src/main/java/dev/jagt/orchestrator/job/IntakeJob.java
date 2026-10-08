package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService.ConfigFile.TrackerConfig;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.task.ActionOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Work jagt takes off the tracker without being asked. The trigger is the tracker's own stage, checked against
 * the facts read back off the item; nothing here judges what an item MEANS.
 */
@Service
@RequiredArgsConstructor
public class IntakeJob implements Job {

    private final ConfigService configService;
    private final IntakeCandidates candidates;
    private final IntakeStart starts;

    @Override
    public String id() {
        return "intake";
    }

    @Override
    public String describe() {
        return "start a task on every item the tracker says is ready for one";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(configService.load().tracker().everyMinutesOrDefault());
    }

    @Override
    public void run() {
        TrackerConfig tracker = configService.load().tracker();
        if (!tracker.modeOrOff().takes() || !tracker.missing().isEmpty()) {
            return;
        }
        // Stamped as the tracker's so the record says who chose each repository, which is what a later
        // routing may learn from and what it must not.
        for (IntakeCandidates.Ready ready : candidates.waiting(starts.held()).orElse(List.of())) {
            if (!OriginContext.as(ActionOrigin.TRACKER, () -> starts.start(ready))) {
                return;
            }
        }
    }
}
