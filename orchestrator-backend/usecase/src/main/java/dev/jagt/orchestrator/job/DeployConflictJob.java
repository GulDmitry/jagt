package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Where the Master acts, a deploy conflict is the session's to resolve and the deploy jagt's to finish: the human
 * already pressed it, so the resolution staged in full is the trigger, not a second press.
 */
@Service
@RequiredArgsConstructor
public class DeployConflictJob implements Job {

    private final ConfigService configService;
    private final DeployConflicts conflicts;
    private final ConflictHandOff handOff;

    @Override
    public String id() {
        return "deploy-conflict";
    }

    @Override
    public String describe() {
        return "hand each deploy conflict to its session, and finish the deploy once it is resolved";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        if (configService.load().master().modeOrDefault() != MasterMode.ACT) {
            return;
        }
        conflicts.waiting().forEach(handOff::handle);
    }
}
