package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.KeepAwake;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.task.MasterMode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "orchestrator.keep-awake", havingValue = "true")
@RequiredArgsConstructor
public class KeepAwakeJob implements Job {

    private final ConfigService config;
    private final KeepAwake keepAwake;

    @Override
    public String id() {
        return "keepawake";
    }

    @Override
    public String describe() {
        return "keep the machine awake while the Master acts, auto-review polls or the tracker hands out work";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public void run() {
        ConfigFile file = config.load();
        if (file.master().modeOrDefault() == MasterMode.ACT || file.autoReview().enabledOrDefault()
                || file.tracker().modeOrOff().takes()) {
            keepAwake.hold();
        } else {
            keepAwake.release();
        }
    }
}
