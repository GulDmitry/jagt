package dev.jagt.orchestrator.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** What each running headless call may touch, keyed by the fence its hook names. */
@Component
public class ReadScopes {

    /**
     * {@code tools} are tool names or globs; {@code roots} bound the file tools; {@code git} admits the read-only
     * git subcommands inside them.
     */
    public record ReadScope(List<Path> roots, List<String> tools, boolean git) {
    }

    private final Map<String, ReadScope> open = new ConcurrentHashMap<>();
    private final Set<String> asked = ConcurrentHashMap.newKeySet();

    public void open(String fence, ReadScope scope) {
        open.put(fence, scope);
    }

    /** False where no call ever asked under the fence: its hook never ran. */
    public boolean close(String fence) {
        open.remove(fence);
        return asked.remove(fence);
    }

    /** The scope held under {@code fence}, counting the call as one the fence heard. */
    public Optional<ReadScope> ask(String fence) {
        Optional<ReadScope> scope = Optional.ofNullable(fence).map(open::get);
        scope.ifPresent(held -> asked.add(fence));
        return scope;
    }
}
