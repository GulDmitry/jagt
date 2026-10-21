package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** What the machine jagt runs on says about itself. */
public interface Host {

    String osName();

    Path home();

    /** The directory the JVM was started in. */
    Path workingDir();

    Optional<String> env(String name);

    boolean onPath(String name);

    /** The path of a bare name found beside the usual install locations; the name itself where nothing matched. */
    String resolve(String name);

    /** Where application bundles are installed, system-wide first; an IDE's launcher sits inside one. */
    List<Path> applicationDirs();

    /** The one jar this JVM was started from; empty when it runs from a directory or several entries. */
    Optional<Path> jar();
}
