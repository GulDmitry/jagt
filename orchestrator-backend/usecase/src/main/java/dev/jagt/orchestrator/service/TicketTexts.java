package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Each task's ticket as last read, held for the round that follows. Lost on restart, when reviewers read it. */
@Component
@RequiredArgsConstructor
public class TicketTexts {

    private record Read(long asOf, Optional<String> text) {
    }

    private final MeteredAssistant assistant;
    private final Map<String, Read> reads = new ConcurrentHashMap<>();

    /** A failed read is booked too: asking again every tick is paid for every tick. */
    public void read(String taskId, String ticketRef, long asOf) {
        Answer<String> answer = assistant.readTicketText(ticketRef);
        assistant.chargeTask(taskId, answer.usage());
        reads.put(taskId, new Read(asOf, answer.facts()));
    }

    public boolean readSince(String taskId, long since) {
        Read read = reads.get(taskId);
        return read != null && read.asOf() >= since;
    }

    public Optional<String> of(String taskId) {
        Read read = reads.get(taskId);
        return read == null ? Optional.empty() : read.text();
    }
}
