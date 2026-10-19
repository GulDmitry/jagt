package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.UsageLimitsProperties;
import dev.jagt.orchestrator.port.SubscriptionUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * One pending wake per task, kept beside {@code state.json} so it outlives a restart. A wake is taken out of
 * the file before it is returned: a crash between the two costs a wake, never sends a second one.
 */
@Component
@Slf4j
public class SessionWakes {

    private static final TypeReference<Map<String, Long>> FILE = new TypeReference<>() { };

    private final UsageLimits limits;
    private final UsageLimitsProperties properties;
    private final ObjectMapper mapper;
    private final Path file;
    private final Clock clock;
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public SessionWakes(UsageLimits limits, UsageLimitsProperties properties, ObjectMapper mapper,
                        OrchestratorPaths paths, Clock clock) {
        this.limits = limits;
        this.properties = properties;
        this.mapper = mapper;
        this.file = paths.stateFile().resolveSibling("session-wakes.json");
        this.clock = clock;
        load();
    }

    /** Arms a wake at the reset of every window at its limit; none where no window is, or one carries no reset. */
    public synchronized void arm(String taskId) {
        if (!properties.wakes()) {
            return;
        }
        Optional<Instant> at = limits.refresh(clock.instant()).map(UsageLimits.Reading::answer)
                .filter(SubscriptionUsage.Read.class::isInstance).map(SubscriptionUsage.Read.class::cast)
                .flatMap(read -> resetOfExhausted(read.snapshot().windows()));
        if (at.isEmpty()) {
            log.atInfo().setMessage("session wake not armed").addKeyValue("task", taskId)
                    .addKeyValue("cause", "no window at its limit with a known reset").log();
            return;
        }
        pending.put(taskId, at.get().toEpochMilli());
        save();
        log.atInfo().setMessage("session wake armed").addKeyValue("task", taskId)
                .addKeyValue("at", at.get()).log();
    }

    public synchronized void cancel(String taskId) {
        if (pending.remove(taskId) != null) {
            save();
            log.atInfo().setMessage("session wake cancelled").addKeyValue("task", taskId).log();
        }
    }

    /** Due wakes not held back by {@code held}; a held one stays pending for the next call. */
    public synchronized List<String> takeDue(Predicate<String> held) {
        long now = clock.millis();
        List<String> due = pending.entrySet().stream().filter(wake -> wake.getValue() <= now)
                .map(Map.Entry::getKey).filter(taskId -> !held.test(taskId)).sorted().toList();
        if (!due.isEmpty()) {
            due.forEach(pending::remove);
            save();
        }
        return due;
    }

    private static Optional<Instant> resetOfExhausted(List<SubscriptionUsage.Window> windows) {
        List<SubscriptionUsage.Window> exhausted = windows.stream().filter(window -> window.percentUsed() >= 100)
                .toList();
        if (exhausted.isEmpty() || exhausted.stream().anyMatch(window -> window.resetsAt().isEmpty())) {
            return Optional.empty();
        }
        return exhausted.stream().map(window -> window.resetsAt().get()).max(Comparator.naturalOrder());
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            pending.putAll(mapper.readValue(Files.readString(file), FILE));
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("session wakes not read").addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "pending wakes dropped").log();
        }
    }

    private void save() {
        try {
            Path written = Files.createTempFile(file.getParent(), "session-wakes", ".tmp");
            Files.writeString(written, mapper.writeValueAsString(Map.copyOf(pending)));
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            log.atWarn().setMessage("session wakes not written").addKeyValue("cause", e.toString())
                    .addKeyValue("effect", "a restart forgets them").log();
        }
    }
}
