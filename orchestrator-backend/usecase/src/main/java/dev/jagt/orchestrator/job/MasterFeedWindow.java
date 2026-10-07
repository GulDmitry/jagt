package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

/** What the Master does, one window for every project; closed by hand, it comes back. */
@Service
@RequiredArgsConstructor
public class MasterFeedWindow implements Job {

    private static final String WINDOW = "master";
    /** Written by the {@code MASTER} appender in {@code logback-spring.xml}. */
    static final Path FEED = Path.of("jagt-master.log");

    private final ConfigService configService;
    private final SessionHost sessions;

    @Override
    public String id() {
        return "master-feed";
    }

    @Override
    public String describe() {
        return "keep the master window following what the Master does";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(10);
    }

    @Override
    public void run() {
        ConfigService.ConfigFile config = configService.load();
        if (!config.master().running()) {
            return;
        }
        sessions.keepFeedWindow(sessions.sessionName(config.viewer().session()), WINDOW, FEED.toAbsolutePath());
    }
}
