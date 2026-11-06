package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClaudeMovedKeysCheck implements StartupCheck {

    private static final String ROOT = "orchestrator.";

    private record Moved(String written, String bound, String now, Bindable<?> shape) {
    }

    private static final List<Moved> MOVED = List.of(
            new Moved("agent.outputStyle", "agent.output-style", "claude.outputStyle", Bindable.of(String.class)),
            new Moved("agentDisabledPlugins", "agent-disabled-plugins", "claude.disabledPlugins",
                    Bindable.listOf(String.class)));

    private final Environment environment;

    @Override
    public List<Problem> problems() {
        Binder binder = Binder.get(environment);
        return MOVED.stream()
                .filter(moved -> binder.bind(ROOT + moved.bound(), moved.shape()).isBound())
                .map(moved -> new Problem(ROOT + moved.written(), ROOT + moved.written()
                        + " is no longer read: it moved to " + ROOT + moved.now() + ". Rename it in jagt.yml."))
                .toList();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
