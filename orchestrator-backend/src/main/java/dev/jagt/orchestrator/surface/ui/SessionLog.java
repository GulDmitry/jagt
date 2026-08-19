package dev.jagt.orchestrator.surface.ui;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * One run, one log. The file is emptied and every archive beside it deleted before logging starts, so the
 * record a human reads back (the {@code activity} report) is this session's and nothing else: yesterday's
 * entries presented as today's work is worse than no history at all, and the archives are mostly noise.
 *
 * <p>Runs in the same gap {@link ConsoleLogging} does — after {@code application.yml} is read, before the
 * appender opens the file — and is registered by hand in {@code main} for the same reason.
 */
public class SessionLog implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment());
    }

    /** @return whether this run cleared the log; false means it deliberately left the file alone. */
    boolean apply(ConfigurableEnvironment environment) {
        String configured = environment.getProperty("logging.file.name", "");
        if (configured.isBlank()) {
            return false;
        }
        // A jagt is already listening, so THIS launch is about to die on the port — and the file belongs to the
        // one still running. Clearing it there would unlink the log a live instance keeps writing to: its
        // appender holds the descriptor, so nothing recreates the path and both the report and `tail -f` go
        // blind for the rest of that process's life.
        if (portTaken(environment.getProperty("server.port", "8290"))) {
            return false;
        }
        start(Path.of(configured));
        return true;
    }

    private static boolean portTaken(String port) {
        try {
            int number = Integer.parseInt(port.trim());
            try (ServerSocket probe = new ServerSocket()) {
                probe.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), number));
                return false;
            }
        } catch (IOException e) {
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Best-effort: a log that cannot be cleared is not a reason to refuse to start. */
    static void start(Path logFile) {
        Path absolute = logFile.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        Path fileName = absolute.getFileName();
        if (directory == null || fileName == null) {
            return;
        }
        String name = fileName.toString();
        try {
            Files.deleteIfExists(absolute);
            if (!Files.isDirectory(directory)) {
                return;                       // logback creates it moments later; there is nothing to sweep yet
            }
            try (Stream<Path> siblings = Files.list(directory)) {
                List<Path> archives = siblings
                        .filter(sibling -> sibling.getFileName().toString().startsWith(name + "."))
                        .toList();
                for (Path archive : archives) {
                    Files.deleteIfExists(archive);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not clear " + absolute + ": " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
