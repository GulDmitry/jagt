package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.port.Host;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class SystemHost implements Host {

    @Override
    public String osName() {
        return System.getProperty("os.name", "");
    }

    @Override
    public Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    @Override
    public Path workingDir() {
        return Path.of(System.getProperty("user.dir"));
    }

    @Override
    public Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    @Override
    public boolean onPath(String name) {
        return Executables.onPath(name);
    }

    @Override
    public String resolve(String name) {
        return Executables.resolve(name);
    }

    @Override
    public Optional<Path> jar() {
        return Optional.ofNullable(jarFromClassPath(System.getProperty("java.class.path"), Files::isRegularFile));
    }

    /**
     * {@code java -jar x.jar} puts EXACTLY that jar on the classpath, the one place the path is unambiguous: inside
     * a fat jar {@code getProtectionDomain().getCodeSource()} answers a {@code jar:nested:…} URL that
     * {@code Path.of} refuses. Anything else means several entries or a directory, and no single jar to watch.
     */
    static @Nullable Path jarFromClassPath(@Nullable String classPath, Predicate<Path> isFile) {
        if (classPath == null || classPath.isBlank() || classPath.contains(File.pathSeparator)) {
            return null;
        }
        Path path = Path.of(classPath.strip()).toAbsolutePath().normalize();
        return path.getFileName().toString().endsWith(".jar") && isFile.test(path) ? path : null;
    }
}
