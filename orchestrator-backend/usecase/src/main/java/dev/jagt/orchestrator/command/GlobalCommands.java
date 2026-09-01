package dev.jagt.orchestrator.command;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every {@link GlobalCommand} there is, collected by the container. */
@Component
public class GlobalCommands {

    private final Map<String, GlobalCommand> byId;

    public GlobalCommands(List<GlobalCommand> commands) {
        this.byId = commands.stream().sorted(Comparator.comparing(GlobalCommand::id))
                .collect(Collectors.toMap(command -> command.id().toLowerCase(Locale.ROOT), Function.identity(),
                        (first, second) -> {
                            throw new IllegalStateException("Two commands declare the verb '" + first.id()
                                    + "': " + first.getClass().getSimpleName() + " and "
                                    + second.getClass().getSimpleName());
                        }, LinkedHashMap::new));
    }

    /** Strict: an unknown word is free text, never the nearest verb. */
    public Optional<GlobalCommand> byId(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(byId.get(id.strip().toLowerCase(Locale.ROOT)));
    }

    public List<GlobalCommand> all() {
        return List.copyOf(byId.values());
    }
}
