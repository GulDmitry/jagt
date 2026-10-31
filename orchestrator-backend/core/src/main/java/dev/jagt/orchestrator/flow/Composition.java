package dev.jagt.orchestrator.flow;

import java.util.List;
import java.util.stream.Stream;

/** The roles a task's chart is composed of, in the order their regions are declared. */
record Composition(List<Role> roles) {

    Composition {
        roles = roles.stream().anyMatch(role -> role.kind() == Role.Kind.ARBITER)
                ? List.copyOf(roles) : Stream.concat(roles.stream(), Stream.of(new MasterRegion())).toList();
    }

    static Composition builtIn() {
        return new Composition(List.of(CheckerRegion.QA));
    }

    List<String> checkers() {
        return roles.stream().filter(role -> role.kind() == Role.Kind.CHECKER).map(Role::id).toList();
    }
}
