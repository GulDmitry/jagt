package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TicketFacts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A task with no title reads on the board as a bare id — the one thing that tells two tickets apart at a
 * glance. So the read still happens for a launch that skipped it, just off the critical path: the agent is
 * already working when the title lands, and a failed read costs the task nothing but its title.
 */
@Service
@Slf4j
public class TicketTitleBackfill {

    private final TicketReader tickets;
    private final StateService stateService;
    private final Executor background;

    @Autowired
    public TicketTitleBackfill(TicketReader tickets, StateService stateService) {
        this(tickets, stateService, Executors.newVirtualThreadPerTaskExecutor());
    }

    TicketTitleBackfill(TicketReader tickets, StateService stateService, Executor background) {
        this.tickets = tickets;
        this.stateService = stateService;
        this.background = background;
    }

    public void of(String taskId) {
        background.execute(() -> {
            try {
                var read = tickets.read(taskId);
                tickets.charge(taskId, read.usage());
                read.facts().filter(TicketFacts::exists).ifPresent(facts -> stateService.updateTask(taskId,
                        task -> task.withTicket(facts.title(), facts.url())));
            } catch (RuntimeException e) {
                log.warn("Could not read a title for {}: {}", taskId, e.getMessage());
            }
        });
    }
}
