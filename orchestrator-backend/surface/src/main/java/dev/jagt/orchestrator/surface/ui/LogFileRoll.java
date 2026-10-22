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
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * This run's log, the previous run's in {@code .1}: runs in the gap after {@code application.yml} is read and before the appender opens the
 * file. Registered by hand rather than declared — {@code bootJar} hoists {@code META-INF/spring/…imports} to
 * the jar root, which is not on the executable jar's classpath.
 */
public class LogFileRoll implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment());
    }

    /** @return whether this run rolled the log; false means it deliberately left the file alone. */
    boolean apply(ConfigurableEnvironment environment) {
        List<String> configured = Stream.of("logging.file.name", "logging.master.name")
                .map(key -> environment.getProperty(key, ""))
                .filter(name -> !name.isBlank())
                .toList();
        if (configured.isEmpty()) {
            return false;
        }
        // Another jagt already holds the file open: moving it leaves that appender writing to the archive.
        if (portTaken(environment.getProperty("server.port", "8290"))) {
            return false;
        }
        configured.forEach(name -> start(Path.of(name)));
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

    /** Best-effort: a log that cannot be rolled is not a reason to refuse to start. */
    static void start(Path logFile) {
        Path absolute = logFile.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        Path fileName = absolute.getFileName();
        if (directory == null || fileName == null || !Files.isDirectory(directory)) {
            return;
        }
        String name = fileName.toString();
        Path previous = directory.resolve(name + ".1");
        try {
            if (Files.exists(absolute)) {
                Files.move(absolute, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Stream<Path> siblings = Files.list(directory)) {
                List<Path> strays = siblings
                        .filter(sibling -> sibling.getFileName().toString().startsWith(name + "."))
                        .filter(sibling -> !sibling.equals(previous))
                        .toList();
                for (Path stray : strays) {
                    Files.deleteIfExists(stray);
                }
            }
        } catch (IOException | RuntimeException e) {
            Console.problem("Could not roll " + absolute + ": " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
