package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.InfraRetries;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ActionOrigin;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.InfraRetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Re-runs a pipeline the host said died on infrastructure, once its wait is over and the host still says so. The
 * one place jagt writes to a code host.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InfraRetryJob implements Job {

    private final InfraRetries retries;
    private final PipelineRetry pipelines;
    private final TaskStops stops;

    @Override
    public String id() {
        return "infra-retry";
    }

    @Override
    public String describe() {
        return "re-run a pipeline that died on infrastructure once its wait is over";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public void run() {
        for (Map.Entry<String, InfraRetry> due : retries.due().entrySet()) {
            try {
                OriginContext.as(ActionOrigin.JAGT, () -> retry(due.getKey(), due.getValue()));
            } catch (RuntimeException e) {
                log.atWarn().setMessage("infra retry failed").addKeyValue("task", due.getKey())
                        .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "tried again next minute").log();
            }
        }
    }

    private void retry(String taskId, InfraRetry armed) {
        if (!retries.stillWaiting(taskId) || !stops.moveDue(taskId)) {
            retries.clear(taskId, false);
            return;
        }
        Answer<List<FailedJob>> failed = pipelines.failedJobs(armed.pipelineUrl());
        if (failed.facts().isEmpty()) {
            retries.refused(taskId, failed.failure());
        } else if (RedCause.of(failed.facts().get()) == RedCause.INFRASTRUCTURE) {
            send(taskId, armed);
        } else {
            retries.clear(taskId, false);
        }
    }

    private void send(String taskId, InfraRetry armed) {
        Answer<String> sent = pipelines.retryFailed(armed.pipelineUrl());
        if (sent.facts().isPresent()) {
            retries.sent(taskId, armed);
            log.atInfo().setMessage("pipeline retried").addKeyValue("task", taskId)
                    .addKeyValue("url", armed.pipelineUrl()).addKeyValue("reason", armed.reason()).log();
        } else {
            retries.refused(taskId, sent.failure());
        }
    }
}
