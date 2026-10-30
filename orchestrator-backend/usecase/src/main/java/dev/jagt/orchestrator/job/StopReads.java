package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.service.RoundReading;
import dev.jagt.orchestrator.service.RoundRecord;
import dev.jagt.orchestrator.service.TaskStops;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Reads a stopped task's checks to end the stop an outside condition set; it changes nothing else of the task. */
@Service
@Slf4j
@RequiredArgsConstructor
class StopReads {

    private final RoundReading reading;
    private final TaskStops stops;
    private final RoundRecord record;

    void probe(String taskId) {
        switch (reading.read(taskId)) {
            case RoundReading.Round round -> {
                record.recordListed(taskId, round.facts(), round.tip());
                Pipeline pipeline = Pipeline.of(round.facts().pipelineStatus());
                stops.endOnRead(taskId, pipeline).ifPresent(stop -> log.atInfo().setMessage("stop voided by read")
                        .addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger())
                        .addKeyValue("pipeline", pipeline).log());
            }
            case RoundReading.Refused refused -> stops.endOnRefusal(taskId, refused.result().kind(), refused.request())
                    .ifPresent(stop -> log.atInfo().setMessage("stop voided by read")
                            .addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger())
                            .addKeyValue("request", "gone").log());
        }
    }
}
