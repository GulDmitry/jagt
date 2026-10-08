package dev.jagt.orchestrator.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** What each running headless call may touch, keyed by the fence its hook names. */
@Component
public class ReadScopes {

    /**
     * {@code tools} are tool names or globs, a bare server name standing for all its tools; {@code roots} bound the
     * file tools; {@code git} admits the read-only git subcommands inside them.
     */
    public record ReadScope(List<Path> roots, List<String> tools, boolean git) {
    }

    private final Map<String, ReadScope> open = new ConcurrentHashMap<>();

    public void open(String fence, ReadScope scope) {
        open.put(fence, scope);
    }

    public void close(String fence) {
        open.remove(fence);
    }

    public Optional<ReadScope> find(String fence) {
        return Optional.ofNullable(fence).map(open::get);
    }
}
