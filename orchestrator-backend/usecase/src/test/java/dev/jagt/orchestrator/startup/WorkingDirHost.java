package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Host;

import java.nio.file.Path;
import java.util.Optional;

record WorkingDirHost(Path workingDir) implements Host {

    @Override
    public String osName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path home() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<String> env(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean onPath(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String resolve(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Path> jar() {
        throw new UnsupportedOperationException();
    }
}
