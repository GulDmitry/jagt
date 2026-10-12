package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;

/** What the Master does, one window for every project; closed by hand, it comes back. */
@Service
public class MasterFeedWindow implements Job {

    private static final String WINDOW = "master";

    private final ConfigService configService;
    private final SessionHost sessions;
    private final Path feed;

    public MasterFeedWindow(ConfigService configService, SessionHost sessions,
                            @Value("${logging.master.name}") String feed) {
        this.configService = configService;
        this.sessions = sessions;
        this.feed = Path.of(feed);
    }

    @Override
    public String id() {
        return "master-feed";
    }

    @Override
    public Serves serves() {
        return Serves.MASTER;
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
        sessions.keepFeedWindow(sessions.sessionName(config.viewer().session()), WINDOW, feed.toAbsolutePath());
    }
}
