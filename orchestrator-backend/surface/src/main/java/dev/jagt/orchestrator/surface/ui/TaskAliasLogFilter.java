package dev.jagt.orchestrator.surface.ui;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TaskAliasLogFilter extends Filter<ILoggingEvent> {

    // Logback builds its appenders before Spring exists, so the aliases cannot be injected.
    private static final Map<String, String> ALIASES = new ConcurrentHashMap<>();

    static void remember(String taskId, String alias) {
        ALIASES.put(taskId, alias);
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (event instanceof LoggingEvent mutable && pairs != null && value(pairs, "alias").isEmpty()) {
            value(pairs, "task").map(ALIASES::get)
                    .ifPresent(alias -> mutable.addKeyValuePair(new KeyValuePair("alias", alias)));
        }
        return FilterReply.NEUTRAL;
    }

    private static Optional<String> value(List<KeyValuePair> pairs, String key) {
        return pairs.stream().filter(pair -> key.equals(pair.key) && pair.value != null)
                .map(pair -> String.valueOf(pair.value)).findFirst();
    }
}
