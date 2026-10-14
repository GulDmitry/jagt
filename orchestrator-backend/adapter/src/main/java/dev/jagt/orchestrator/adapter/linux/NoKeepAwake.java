package dev.jagt.orchestrator.adapter.linux;

import dev.jagt.orchestrator.port.KeepAwake;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "orchestrator", name = "platform", havingValue = "linux")
public class NoKeepAwake implements KeepAwake {

    @Override
    public void hold() {
    }

    @Override
    public void release() {
    }
}
