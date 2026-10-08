package dev.jagt.orchestrator.surface.mcp;

import dev.jagt.orchestrator.protocol.Message;
import dev.jagt.orchestrator.protocol.MessageContext;
import dev.jagt.orchestrator.protocol.Violation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Admitting the caller, reading a tool's arguments as a message, judging it, and only then running it — in ONE
 * place, so that a tool cannot be the one that skipped a step and a test cannot exercise a path the server does
 * not take.
 */
public final class MessageTool {

    private MessageTool() {
    }

    public static <T extends Message> ToolHandler of(ObjectMapper mapper, String name, Audience audience,
                                                     Class<T> message,
                                                     BiFunction<T, String, MessageContext> context,
                                                     MessageHandler<T> handler) {
        return (given, callerTaskId) -> {
            JsonNode args = given.isMissingNode() || given.isNull() ? mapper.createObjectNode() : given;
            if (!audience.admits(callerTaskId)) {
                throw new ToolRefusal(ToolFailure.PERMISSION, name + " is Master-only: a sub-agent may only act"
                        + " inside its own worktree");
            }
            T said;
            try {
                said = mapper.treeToValue(args, message);
            } catch (JacksonException unreadable) {
                throw new ToolRefusal(ToolFailure.VALIDATION, unreadable.getOriginalMessage());
            }
            List<Violation> violations = new ArrayList<>(misspelled(args, message));
            violations.addAll(said.violations(context.apply(said, callerTaskId)));
            if (!violations.isEmpty()) {
                throw new ToolRefusal(ToolFailure.VALIDATION, Message.refusal(violations));
            }
            return handler.call(said, callerTaskId);
        };
    }

    /** A misspelling of a field the call left out would otherwise run the call on that field's default. */
    private static List<Violation> misspelled(JsonNode args, Class<? extends Message> message) {
        Map<String, String> declared = Arrays.stream(message.getRecordComponents())
                .collect(Collectors.toMap(component -> folded(component.getName()), RecordComponent::getName));
        return args.properties().stream().map(Map.Entry::getKey)
                .filter(name -> !declared.containsValue(name))
                .filter(name -> declared.containsKey(folded(name)) && !args.has(declared.get(folded(name))))
                .map(name -> new Violation(name, "not a field; did you mean " + declared.get(folded(name)) + "?"))
                .toList();
    }

    private static String folded(String name) {
        return name.replaceAll("[_-]", "").toLowerCase(Locale.ROOT);
    }
}
