package dev.jagt.orchestrator.service.stop;

import dev.jagt.orchestrator.task.review.LoopBreaker;
import org.springframework.stereotype.Component;

/** What decides whether a task stops, how it is held, and who hears of it. */
@Component
public record StopPolicy(
    PhaseProfiles profiles, LoopBreaker.Current breakers, StopAnnouncements announcements) {}
