package dev.jagt.orchestrator.surface.ui;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * This run's log, the previous run's in {@code .1}, the runs before it dated beside it: runs in the gap after
 * {@code application.yml} is read and before the appender opens the file. Registered by hand rather than declared —
 * {@code bootJar} hoists {@code META-INF/spring/…imports} to the jar root, which is not on the executable jar's classpath.
 */
public class LogFileRoll implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        apply(event.getEnvironment());
    }

    /** Whether this run rolled the log; false means it deliberately left the file alone. */
    boolean apply(ConfigurableEnvironment environment) {
        List<String> configured = Stream.of("logging.file.name", "logging.master.name")
                .map(key -> environment.getProperty(key, ""))
                .filter(name -> !name.isBlank())
                .toList();
        if (configured.isEmpty()) {
            return false;
        }
        // Another jagt already holds the file open: moving it leaves that appender writing to the archive.
        if (BoardPort.taken(BoardPort.configured(environment))) {
            return false;
        }
        configured.forEach(name -> start(Path.of(name)));
        return true;
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
            if (Files.isRegularFile(previous)) {
                Files.move(previous, LogArchives.archiveName(previous, name), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.isRegularFile(absolute)) {
                Files.move(absolute, previous, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Stream<Path> siblings = Files.list(directory)) {
                List<Path> strays = siblings
                        .filter(sibling -> sibling.getFileName().toString().startsWith(name + "."))
                        .filter(sibling -> !sibling.equals(previous))
                        .filter(sibling -> !LogArchives.isArchive(sibling, name))
                        .filter(Files::isRegularFile)
                        .toList();
                for (Path stray : strays) {
                    Files.deleteIfExists(stray);
                }
            }
            LogArchives.prune(directory, name);
        } catch (IOException | RuntimeException e) {
            Console.problem("Could not roll " + absolute + ": " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
