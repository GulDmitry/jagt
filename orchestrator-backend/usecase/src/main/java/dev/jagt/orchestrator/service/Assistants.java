package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.CodeHostAssistant;
import dev.jagt.orchestrator.port.RoundReviewer;
import dev.jagt.orchestrator.port.RoutingAssistant;
import dev.jagt.orchestrator.port.TrackerAssistant;
import org.springframework.stereotype.Component;

/** What a headless model answers: about a code host, a tracker, a routing, a review round. */
@Component
public record Assistants(
    CodeHostAssistant codeHost,
    TrackerAssistant tracker,
    RoutingAssistant routing,
    RoundReviewer reviewer) {}
