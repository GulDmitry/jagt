package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.InstalledRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** A role that did not compose is left out and said so; it never stops the start. */
@Component
@RequiredArgsConstructor
public class RoleCheck implements StartupCheck {

    private final InstalledRoles roles;

    @Override
    public List<Problem> problems() {
        return List.of();
    }

    @Override
    public List<Problem> notes() {
        return roles.skipped();
    }

    @Override
    public boolean machine() {
        return false;
    }
}
