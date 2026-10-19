package dev.jagt.orchestrator.port;

import java.nio.file.Path;
import java.util.Optional;

/** What the machine jagt runs on says about itself. */
public interface Host {

    String osName();

    Path home();

    Optional<String> env(String name);

    boolean onPath(String name);

    /** The path of a bare name found beside the usual install locations; the name itself where nothing matched. */
    String resolve(String name);

    /** The one jar this JVM was started from; empty when it runs from a directory or several entries. */
    Optional<Path> jar();
}
