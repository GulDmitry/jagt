package dev.jagt.orchestrator.flow;

import java.util.List;

/** The roles a task's chart is composed of, in the order their regions are declared. */
record Composition(List<Role> roles) {

    Composition {
        roles = List.copyOf(roles);
    }

    static Composition builtIn() {
        return new Composition(List.of(CheckerRegion.QA));
    }

    List<String> checkers() {
        return roles.stream().filter(role -> role.kind() == Role.Kind.CHECKER).map(Role::id).toList();
    }
}
